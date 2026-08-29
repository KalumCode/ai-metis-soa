import { type KeyboardEvent, useRef, useState } from "react";
import { SendHorizontal, Square } from "lucide-react";

import { cn } from "@/shared/ui/class-name";

/** 输入区：Enter 发送 / Shift+Enter 换行；运行中显示停止按钮。 */
export function ChatComposer({
  disabled,
  onSend,
  onStop,
}: {
  disabled: boolean;
  onSend: (text: string) => void;
  onStop?: () => void;
}) {
  const [text, setText] = useState("");
  const textareaRef = useRef<HTMLTextAreaElement>(null);

  const submit = () => {
    const trimmed = text.trim();
    if (!trimmed) {
      return;
    }
    onSend(trimmed);
    setText("");
    if (textareaRef.current) {
      textareaRef.current.style.height = "auto";
    }
  };

  const handleKeyDown = (event: KeyboardEvent<HTMLTextAreaElement>) => {
    if (event.key === "Enter" && !event.shiftKey && !event.nativeEvent.isComposing) {
      event.preventDefault();
      if (!disabled) {
        submit();
      }
    }
  };

  return (
    <div className="shrink-0 border-t border-app-border px-6 py-4">
      <div className="mx-auto flex max-w-3xl items-end gap-2">
        <textarea
          ref={textareaRef}
          value={text}
          onChange={(event) => {
            setText(event.target.value);
            // 自适应高度
            event.target.style.height = "auto";
            event.target.style.height = `${Math.min(event.target.scrollHeight, 160)}px`;
          }}
          onKeyDown={handleKeyDown}
          placeholder={disabled ? "回复生成中…" : "输入消息，Enter 发送，Shift+Enter 换行"}
          disabled={disabled}
          rows={1}
          className={cn(
            "max-h-40 min-h-[44px] flex-1 resize-none rounded-xl border border-app-border bg-app-panel px-4 py-3 text-sm text-app-text",
            "placeholder:text-app-text-faint focus:border-app-accent focus:outline-none",
            "disabled:opacity-60",
          )}
        />
        {onStop ? (
          <button
            type="button"
            onClick={onStop}
            className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-red-500/15 text-red-400 hover:bg-red-500/25"
            title="停止生成"
          >
            <Square className="h-4 w-4 fill-current" />
          </button>
        ) : (
          <button
            type="button"
            onClick={submit}
            disabled={disabled || !text.trim()}
            className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-app-accent text-white transition-opacity hover:opacity-90 disabled:opacity-40"
            title="发送"
          >
            <SendHorizontal className="h-4 w-4" />
          </button>
        )}
      </div>
    </div>
  );
}
