import { useState } from "react";
import { Check, ChevronDown } from "lucide-react";

import { useAppStore } from "@/store/app-store";
import { cn } from "@/shared/ui/class-name";

/**
 * 模型选择器：输入区的轻量下拉，列出"默认模型"与模型配置管理中的全部配置。
 * 选中状态为全局（app-store.selectedModelId），发送 chat.send 时按配置 id 整体切换。
 */
export function ModelSelector({ disabled }: { disabled?: boolean }) {
  const modelConfigs = useAppStore((s) => s.modelConfigs);
  const selectedModelId = useAppStore((s) => s.selectedModelId);
  const setSelectedModelId = useAppStore((s) => s.setSelectedModelId);
  const [open, setOpen] = useState(false);

  const selected = modelConfigs.find((c) => c.id === selectedModelId) ?? null;

  return (
    <div className="relative">
      <button
        type="button"
        onClick={() => setOpen((prev) => !prev)}
        disabled={disabled}
        className={cn(
          "flex items-center gap-1 rounded-lg px-2 py-1 text-xs transition-colors",
          "text-app-text-dim hover:bg-app-panel-hover hover:text-app-text disabled:opacity-50",
        )}
        title={selected ? `${selected.modelName} · ${selected.baseUrl}` : "使用服务端默认模型配置"}
      >
        <span className="max-w-56 truncate">{selected ? selected.modelName : "默认模型"}</span>
        <ChevronDown className={cn("h-3.5 w-3.5 transition-transform", open && "rotate-180")} />
      </button>

      {open ? (
        <>
          {/* 透明遮罩：点击任意处关闭下拉 */}
          <div className="fixed inset-0 z-40" onClick={() => setOpen(false)} />
          <div
            className={cn(
              "absolute bottom-full left-0 z-50 mb-1 w-72 rounded-xl border border-app-border",
              "bg-app-panel py-1 shadow-lg",
            )}
            onClick={(event) => event.stopPropagation()}
          >
            <ModelOption
              label="默认模型"
              hint="服务端默认模型配置"
              selected={selectedModelId === null}
              onSelect={() => {
                setSelectedModelId(null);
                setOpen(false);
              }}
            />
            {modelConfigs.map((config) => (
              <ModelOption
                key={config.id}
                label={config.modelName}
                hint={config.baseUrl}
                selected={config.id === selectedModelId}
                onSelect={() => {
                  setSelectedModelId(config.id);
                  setOpen(false);
                }}
              />
            ))}
          </div>
        </>
      ) : null}
    </div>
  );
}

function ModelOption({
  label,
  hint,
  selected,
  onSelect,
}: {
  label: string;
  hint: string;
  selected: boolean;
  onSelect: () => void;
}) {
  return (
    <button
      type="button"
      onClick={onSelect}
      className={cn(
        "flex w-full items-center gap-2 px-3 py-2 text-left transition-colors",
        selected ? "bg-app-accent-soft" : "hover:bg-app-panel-hover",
      )}
    >
      <Check
        className={cn("h-3.5 w-3.5 shrink-0", selected ? "text-app-accent" : "opacity-0")}
      />
      <span className="min-w-0">
        <span className="block truncate text-sm text-app-text">{label}</span>
        <span className="block truncate text-xs text-app-text-faint" title={hint}>
          {hint}
        </span>
      </span>
    </button>
  );
}
