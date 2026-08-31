import { useState } from "react";
import { Brain, Check, ChevronDown, Copy, User } from "lucide-react";

import type { ChatMessage } from "@/store/app-store";
import { cn } from "@/shared/ui/class-name";

/** 单条消息气泡。 */
export function ChatMessageItem({ message }: { message: ChatMessage }) {
  const isUser = message.role === "user";
  return (
    <div className={cn("flex gap-3", isUser ? "flex-row-reverse" : "flex-row")}>
      <span
        className={cn(
          "mt-1 flex h-8 w-8 shrink-0 items-center justify-center rounded-lg",
          isUser ? "bg-user-bubble text-white" : "bg-app-accent/20 text-app-accent",
        )}
      >
        {isUser ? <User className="h-4 w-4" /> : <BotMark />}
      </span>
      <div className={cn("min-w-0 max-w-[75%]", isUser && "flex flex-col items-end")}>
        {message.thinking ? <ThinkingBlock text={message.thinking} /> : null}
        <div
          className={cn(
            "rounded-2xl px-4 py-2.5 text-sm leading-relaxed whitespace-pre-wrap break-words",
            isUser
              ? "bg-user-bubble text-white"
              : "bg-app-panel border border-app-border text-app-text",
            message.pending && !message.content && !message.thinking && "animate-pulse",
          )}
        >
          {message.content || (message.pending ? "…" : "")}
          {message.pending && message.content ? (
            <span className="ml-0.5 inline-block h-3.5 w-2 animate-pulse bg-app-text-dim align-middle" />
          ) : null}
        </div>
        {message.error ? (
          <div className="mt-1 text-xs text-red-400">{message.error}</div>
        ) : null}
        <div className="mt-1 flex items-center gap-1.5 px-1 text-2xs text-app-text-faint">
          <span>
            {new Date(message.timestamp).toLocaleTimeString("zh-CN", {
              hour: "2-digit",
              minute: "2-digit",
            })}
          </span>
          {!isUser && message.content ? <CopyButton text={message.content} /> : null}
        </div>
      </div>
    </div>
  );
}

/** 复制按钮：复制成功后短暂显示为对勾。 */
function CopyButton({ text }: { text: string }) {
  const [copied, setCopied] = useState(false);
  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(text);
    } catch {
      // WebView2 非 secure context 等场景下回退到 execCommand
      const textarea = document.createElement("textarea");
      textarea.value = text;
      textarea.style.position = "fixed";
      textarea.style.opacity = "0";
      document.body.appendChild(textarea);
      textarea.select();
      document.execCommand("copy");
      document.body.removeChild(textarea);
    }
    setCopied(true);
    window.setTimeout(() => setCopied(false), 1500);
  };
  return (
    <button
      type="button"
      onClick={handleCopy}
      title={copied ? "已复制" : "复制"}
      className="flex h-5 w-5 items-center justify-center rounded text-app-text-faint transition-colors hover:bg-app-panel-hover hover:text-app-text-dim"
    >
      {copied ? <Check className="h-3 w-3" /> : <Copy className="h-3 w-3" />}
    </button>
  );
}

/** 思考过程（可折叠）。 */
function ThinkingBlock({ text }: { text: string }) {
  const [open, setOpen] = useState(false);
  return (
    <div className="mb-1.5 overflow-hidden rounded-xl border border-app-border bg-app-panel/60">
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        className="flex w-full items-center gap-1.5 px-3 py-1.5 text-xs text-app-text-dim hover:bg-app-panel-hover"
      >
        <Brain className="h-3.5 w-3.5" />
        思考过程
        <ChevronDown className={cn("h-3.5 w-3.5 transition-transform", open && "rotate-180")} />
      </button>
      {open ? (
        <div className="max-h-64 overflow-y-auto whitespace-pre-wrap break-words px-3 pb-2.5 text-xs leading-relaxed text-app-text-dim">
          {text}
        </div>
      ) : null}
    </div>
  );
}

function BotMark() {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" className="h-4 w-4">
      <rect x="4" y="8" width="16" height="12" rx="2" strokeLinecap="round" />
      <path strokeLinecap="round" d="M12 8V4M8 4h8" />
      <circle cx="9" cy="13" r="1" fill="currentColor" stroke="none" />
      <circle cx="15" cy="13" r="1" fill="currentColor" stroke="none" />
    </svg>
  );
}
