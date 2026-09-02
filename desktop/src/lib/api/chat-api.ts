import { randomUUID } from "@/lib/format/id";

import { getBackendUrl } from "./backend-url";

import type { ChatRequest, ChatResponse } from "@/types/conversation/chat-protocol";

const SOURCE_CHANNEL = "601";
const DEFAULT_USER_ID = "10000";

export interface ChatStreamCallbacks {
  /** 收到一个协议响应事件。 */
  onEvent: (event: ChatResponse) => void;
  /** 流结束（正常完成）。 */
  onDone: () => void;
  /** 流错误。 */
  onError: (message: string) => void;
}

export interface ChatStreamHandle {
  /** 中断流（对应后端 dispose；配合 chat.stop 请求使用）。 */
  abort: () => void;
}

function buildEnvelope(
  method: ChatRequest["method"],
  sessionId: string,
  message?: string,
  model?: string,
): ChatRequest {
  return {
    type: "req",
    xYunId: randomUUID(),
    xYunVersion: "v1",
    method,
    params: {
      sessionId,
      userId: DEFAULT_USER_ID,
      sourceChannel: SOURCE_CHANNEL,
      ...(message !== undefined ? { message } : {}),
      ...(model ? { model } : {}),
    },
  };
}

/**
 * 发起 chat.send 并逐事件解析 SSE 响应。
 *
 * 使用 fetch + ReadableStream 手工解析 SSE（POST 请求无法用 EventSource）。
 */
export async function sendChat(
  sessionId: string,
  message: string,
  callbacks: ChatStreamCallbacks,
  options?: { model?: string },
): Promise<ChatStreamHandle> {
  const controller = new AbortController();
  const request = buildEnvelope("chat.send", sessionId, message, options?.model);

  void (async () => {
    try {
      const response = await fetch(`${getBackendUrl()}/api/v1/chat`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Accept: "text/event-stream",
        },
        body: JSON.stringify(request),
        signal: controller.signal,
      });
      if (!response.ok || !response.body) {
        throw new Error(`请求失败: HTTP ${response.status}`);
      }

      const reader = response.body.getReader();
      const decoder = new TextDecoder();
      let buffer = "";
      for (;;) {
        const { done, value } = await reader.read();
        if (done) {
          break;
        }
        buffer += decoder.decode(value, { stream: true });
        // SSE 事件以空行分隔
        const blocks = buffer.split("\n\n");
        buffer = blocks.pop() ?? "";
        for (const block of blocks) {
          for (const line of block.split("\n")) {
            if (!line.startsWith("data:")) {
              continue;
            }
            const raw = line.slice(5).trim();
            if (!raw) {
              continue;
            }
            try {
              callbacks.onEvent(JSON.parse(raw) as ChatResponse);
            } catch {
              // 单事件解析失败不中断整个流
            }
          }
        }
      }
      callbacks.onDone();
    } catch (error) {
      if (controller.signal.aborted) {
        callbacks.onDone();
        return;
      }
      callbacks.onError(error instanceof Error ? error.message : String(error));
    }
  })();

  return { abort: () => controller.abort() };
}

/** 发送 chat.stop（中断服务端该会话的运行）。 */
export async function stopChat(sessionId: string): Promise<void> {
  const request = buildEnvelope("chat.stop", sessionId);
  await fetch(`${getBackendUrl()}/api/v1/chat`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(request),
  }).catch(() => {
    // 停止请求失败不阻塞界面
  });
}
