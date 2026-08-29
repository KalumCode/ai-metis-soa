import { useEffect } from "react";

import { useAppStore, useRunningStore, type ChatMessage } from "@/store/app-store";
import { ChatMessageList } from "./chat-message-list";
import { ChatComposer } from "./chat-composer";
import { useChatStream } from "./use-chat-stream";

/** 单个会话的对话视图：消息列表 + 输入框。 */
export function ConversationView({ sessionId }: { sessionId: string }) {
  const session = useAppStore((s) => s.sessions.find((item) => item.id === sessionId));
  const upsertMessage = useAppStore((s) => s.upsertMessage);
  const patchMessage = useAppStore((s) => s.patchMessage);
  const startRunning = useRunningStore((s) => s.startRunning);
  const stopRunning = useRunningStore((s) => s.stopRunning);
  const isRunning = useRunningStore((s) => Boolean(s.runningSessionIds[sessionId]));
  const { send, stop } = useChatStream(sessionId);

  // 离开页面时防止僵尸 pending 消息
  useEffect(
    () => () => {
      stopRunning(sessionId);
    },
    [sessionId, stopRunning],
  );

  if (!session) {
    return null;
  }

  const handleSend = (text: string) => {
    const trimmed = text.trim();
    if (!trimmed || isRunning) {
      return;
    }
    const userMessage: ChatMessage = {
      id: crypto.randomUUID(),
      role: "user",
      content: trimmed,
      timestamp: Date.now(),
    };
    upsertMessage(sessionId, userMessage);
    startRunning(sessionId);
    void send(trimmed);
  };

  const handleStop = () => {
    stop();
    stopRunning(sessionId);
    // 把仍处 pending 的助手消息标记为已截断
    session.messages
      .filter((m) => m.role === "assistant" && m.pending)
      .forEach((m) =>
        patchMessage(sessionId, m.id, { pending: false, error: "已手动停止" }),
      );
  };

  return (
    <div className="flex h-full min-w-0 flex-col">
      <header className="flex h-12 shrink-0 items-center border-b border-app-border px-5 text-sm text-app-text-dim">
        {session.title}
      </header>
      <ChatMessageList sessionId={sessionId} />
      <ChatComposer disabled={isRunning} onSend={handleSend} onStop={isRunning ? handleStop : undefined} />
    </div>
  );
}
