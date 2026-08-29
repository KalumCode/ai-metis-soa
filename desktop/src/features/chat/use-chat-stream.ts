import { useCallback, useRef } from "react";

import { sendChat, stopChat, type ChatStreamHandle } from "@/lib/api/chat-api";
import { randomUUID } from "@/lib/format/id";
import { useAppStore, useRunningStore } from "@/store/app-store";

/**
 * 单个会话的流式对话 hook。
 *
 * 职责：驱动 chat.send SSE 流，把协议事件累积进 store 的助手消息中。
 * 事件消费规则：
 * - assistant + thinking：累积 thinking 字段
 * - assistant + text：累积 content 字段
 * - lifecycle final + isError：标记错误
 * - 其余（tool/item/command_output/lifecycle）暂不展示，留待后续渲染
 */
export function useChatStream(sessionId: string) {
  const upsertMessage = useAppStore((s) => s.upsertMessage);
  const patchMessage = useAppStore((s) => s.patchMessage);
  const stopRunning = useRunningStore((s) => s.stopRunning);
  const handleRef = useRef<ChatStreamHandle | null>(null);

  const send = useCallback(
    (message: string) => {
      const assistantId = randomUUID();
      upsertMessage(sessionId, {
        id: assistantId,
        role: "assistant",
        content: "",
        thinking: "",
        timestamp: Date.now(),
        pending: true,
      });

      let content = "";
      let thinking = "";

      const finalize = (error?: string) => {
        patchMessage(sessionId, assistantId, {
          content,
          thinking,
          pending: false,
          ...(error ? { error } : {}),
        });
        stopRunning(sessionId);
      };

      return sendChat(sessionId, message, {
        onEvent: (event) => {
          const payload = event.payload;
          if (payload.isError) {
            content = content || "";
            finalize(payload.text ?? "服务返回错误");
            return;
          }
          if (payload.stream !== "assistant") {
            return;
          }
          const kind = payload.data?.kind;
          const delta = payload.delta ?? "";
          // phase=final 时以 text 为准（比逐 delta 拼接更可靠）
          if (payload.phase === "final" && payload.text !== undefined) {
            if (kind === "thinking") {
              thinking = payload.text;
            } else {
              content = payload.text;
            }
          } else if (delta) {
            if (kind === "thinking") {
              thinking += delta;
            } else if (kind === "text") {
              content += delta;
            }
          }
          patchMessage(sessionId, assistantId, { content, thinking });
        },
        onDone: () => finalize(),
        onError: (message) => finalize(message),
      }).then((handle) => {
        handleRef.current = handle;
      });
    },
    [sessionId, upsertMessage, patchMessage, stopRunning],
  );

  const stop = useCallback(() => {
    handleRef.current?.abort();
    handleRef.current = null;
    void stopChat(sessionId);
  }, [sessionId]);

  return { send, stop };
}
