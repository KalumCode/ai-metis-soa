import { useEffect, useState } from "react";
import { Pencil, PlugZap, Plus, Trash2 } from "lucide-react";

import { ConfirmModal } from "./confirm-modal";
import { ModelConfigFormModal } from "./model-config-form-modal";
import { deleteModelConfig, fetchModelConfigs, testModelConfig } from "@/lib/api/model-config-api";
import type { ModelConfig, ModelTestResult } from "@/types/model-config/model-config";
import { useAppStore } from "@/store/app-store";
import { cn } from "@/shared/ui/class-name";

type ModalState =
  | { mode: "closed" }
  | { mode: "create" }
  | { mode: "edit"; config: ModelConfig };

/** 模型配置页：列表 + 添加 / 编辑 / 删除 + 连通性测试。 */
export function ModelConfigPage() {
  const setModelConfigs = useAppStore((s) => s.setModelConfigs);
  const [configs, setConfigs] = useState<ModelConfig[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [modalState, setModalState] = useState<ModalState>({ mode: "closed" });
  const [deleting, setDeleting] = useState<ModelConfig | null>(null);
  const [testingIds, setTestingIds] = useState<ReadonlySet<string>>(new Set());
  const [testResults, setTestResults] = useState<Record<string, ModelTestResult>>({});

  const reload = async () => {
    try {
      setError(null);
      const configs = await fetchModelConfigs();
      setConfigs(configs);
      // 同步到全局 store，对话界面的模型选择器即时校正
      setModelConfigs(configs);
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    reload();
  }, []);

  const handleRowTest = async (config: ModelConfig) => {
    if (testingIds.has(config.id)) {
      return;
    }
    setTestingIds((prev) => new Set(prev).add(config.id));
    setTestResults((prev) => {
      const next = { ...prev };
      delete next[config.id];
      return next;
    });
    try {
      const result = await testModelConfig({
        baseUrl: config.baseUrl,
        apiKey: config.apiKey,
        modelName: config.modelName,
      });
      setTestResults((prev) => ({ ...prev, [config.id]: result }));
    } catch (e) {
      setTestResults((prev) => ({
        ...prev,
        [config.id]: { success: false, message: e instanceof Error ? e.message : String(e) },
      }));
    } finally {
      setTestingIds((prev) => {
        const next = new Set(prev);
        next.delete(config.id);
        return next;
      });
    }
  };

  const handleDelete = async () => {
    if (!deleting) {
      return;
    }
    try {
      await deleteModelConfig(deleting.id);
      setDeleting(null);
      await reload();
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
      setDeleting(null);
    }
  };

  return (
    <div className="flex h-full flex-col overflow-hidden">
      <div className="flex shrink-0 items-center justify-between border-b border-app-border px-6 py-4">
        <div>
          <h2 className="text-base font-medium text-app-text">模型配置</h2>
          <p className="mt-0.5 text-xs text-app-text-dim">管理模型接入地址与密钥，保存后落盘在后端配置文件</p>
        </div>
        <button
          type="button"
          onClick={() => setModalState({ mode: "create" })}
          className="flex items-center gap-1.5 rounded-lg bg-app-accent px-4 py-2 text-sm text-white hover:opacity-90"
        >
          <Plus className="h-4 w-4" />
          添加模型
        </button>
      </div>

      <div className="flex-1 overflow-y-auto px-6 py-4">
        {error && <p className="mb-3 text-sm text-red-500">{error}</p>}
        {loading ? (
          <p className="py-8 text-center text-sm text-app-text-dim">加载中...</p>
        ) : configs.length === 0 ? (
          <div className="flex h-full items-center justify-center">
            <div className="text-center">
              <p className="text-sm text-app-text-dim">暂无模型配置</p>
              <p className="mt-1 text-xs text-app-text-faint">点击右上角"添加模型"创建第一条配置</p>
            </div>
          </div>
        ) : (
          <ul className="flex flex-col gap-1">
            {configs.map((config) => {
              const result = testResults[config.id];
              return (
                <li
                  key={config.id}
                  className="flex items-center gap-3 rounded-lg px-2 py-2 transition-colors hover:bg-app-panel-hover"
                >
                  <span
                    className={cn(
                      "h-2 w-2 shrink-0 rounded-full",
                      result ? (result.success ? "bg-emerald-500" : "bg-red-500") : "bg-app-border",
                    )}
                    title={result ? (result.success ? "连接成功" : `连接失败：${result.message}`) : "未测试"}
                  />
                  <div className="flex min-w-0 flex-1 flex-col">
                    <span className="truncate text-sm font-medium text-app-text">{config.modelName}</span>
                    <span className="truncate text-xs text-app-text-dim" title={config.baseUrl}>
                      {config.baseUrl}
                    </span>
                  </div>
                  <span className="max-w-40 shrink-0 truncate text-xs text-app-text-faint" title={config.apiKey}>
                    {config.apiKey || "无密钥"}
                  </span>
                  <div className="flex shrink-0 items-center gap-1">
                    <IconButton
                      title="测试连接"
                      disabled={testingIds.has(config.id)}
                      onClick={() => handleRowTest(config)}
                    >
                      <PlugZap className={cn("h-4 w-4", testingIds.has(config.id) && "animate-pulse")} />
                    </IconButton>
                    <IconButton title="编辑" onClick={() => setModalState({ mode: "edit", config })}>
                      <Pencil className="h-4 w-4" />
                    </IconButton>
                    <IconButton title="删除" onClick={() => setDeleting(config)}>
                      <Trash2 className="h-4 w-4" />
                    </IconButton>
                  </div>
                </li>
              );
            })}
          </ul>
        )}
      </div>

      {modalState.mode !== "closed" && (
        <ModelConfigFormModal
          initial={modalState.mode === "edit" ? modalState.config : null}
          onClose={() => setModalState({ mode: "closed" })}
          onSaved={() => {
            setModalState({ mode: "closed" });
            reload();
          }}
        />
      )}

      <ConfirmModal
        open={deleting !== null}
        title="删除模型配置"
        message={`确定删除模型配置 "${deleting?.modelName ?? ""}" 吗？删除后不可恢复。`}
        confirmText="删除"
        onConfirm={handleDelete}
        onCancel={() => setDeleting(null)}
      />
    </div>
  );
}

/** 行内图标操作按钮。 */
function IconButton({
  title,
  disabled,
  onClick,
  children,
}: {
  title: string;
  disabled?: boolean;
  onClick: () => void;
  children: React.ReactNode;
}) {
  return (
    <button
      type="button"
      title={title}
      onClick={onClick}
      disabled={disabled}
      className="rounded-md p-1.5 text-app-text-dim transition-colors hover:bg-app-accent-soft hover:text-app-accent disabled:opacity-50"
    >
      {children}
    </button>
  );
}
