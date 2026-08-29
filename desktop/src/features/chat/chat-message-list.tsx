import { useEffect, useRef } from "react";

import { useActiveSession } from "@/store/app-store";
import { ChatMessageItem } from "./chat-message-item";

/** 消息列表：自动滚动到底部。 */
export function ChatMessageList({ sessionId }: { sessionId: string }) {
  const session = useActiveSession();
  const scrollRef = useRef<HTMLDivElement>(null);
  const messages = session?.id === sessionId ? session.messages : [];

  useEffect(() => {
    const el = scrollRef.current;
    if (el) {
      el.scrollTop = el.scrollHeight;
    }
  }, [messages.length, messages[messages.length - 1]?.content, messages[messages.length - 1]?.thinking]);

  return (
    <div ref={scrollRef} className="min-h-0 flex-1 overflow-y-auto px-6 py-4">
      {messages.length === 0 ? (
        <div className="flex h-full items-center justify-center text-sm text-app-text-faint">
          开始你的第一个对话
        </div>
      ) : (
        <div className="mx-auto flex max-w-3xl flex-col gap-4">
          {messages.map((message) => (
            <ChatMessageItem key={message.id} message={message} />
          ))}
        </div>
      )}
    </div>
  );
}
