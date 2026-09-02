import { useState } from "react";

import { createModelConfig, testModelConfig, updateModelConfig } from "@/lib/api/model-config-api";
import type { ModelConfig, ModelTestResult } from "@/types/model-config/model-config";
import { cn } from "@/shared/ui/class-name";

const INPUT_CLASS =
  "w-full rounded-xl border border-app-border bg-app-panel px-4 py-3 text-sm text-app-text " +
  "placeholder:text-app-text-faint focus:border-app-accent focus:outline-none";

/** 新增 / 编辑共用的模型配置表单 Modal，footer 内置"测试连接"（用当前表单值直接测，不要求先保存）。 */
export function ModelConfigFormModal({
  initial,
  onClose,
  onSaved,
}: {
  /** null 表示新增，否则为待编辑的配置。 */
  initial: ModelConfig | null;
  onClose: () => void;
  onSaved: () => void;
}) {
  const [baseUrl, setBaseUrl] = useState(initial?.baseUrl ?? "");
  const [apiKey, setApiKey] = useState(initial?.apiKey ?? "");
  const [modelName, setModelName] = useState(initial?.modelName ?? "");
  const [errors, setErrors] = useState<{ baseUrl?: string; modelName?: string }>({});
  const [testing, setTesting] = useState(false);
  const [testResult, setTestResult] = useState<ModelTestResult | null>(null);
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState<string | null>(null);

  const buildRequest = () => {
    const trimmedBaseUrl = baseUrl.trim();
    const trimmedModelName = modelName.trim();
    const nextErrors: { baseUrl?: string; modelName?: string } = {};
    if (!trimmedBaseUrl) {
      nextErrors.baseUrl = "baseUrl 不能为空";
    } else if (!trimmedBaseUrl.startsWith("http://") && !trimmedBaseUrl.startsWith("https://")) {
      nextErrors.baseUrl = "必须以 http:// 或 https:// 开头";
    }
    if (!trimmedModelName) {
      nextErrors.modelName = "模型名称不能为空";
    }
    setErrors(nextErrors);
    if (Object.keys(nextErrors).length > 0) {
      return null;
    }
    return { baseUrl: trimmedBaseUrl, apiKey: apiKey.trim(), modelName: trimmedModelName };
  };

  const handleTest = async () => {
    const request = buildRequest();
    if (!request || testing) {
      return;
    }
    setTesting(true);
    setTestResult(null);
    try {
      setTestResult(await testModelConfig(request));
    } catch (error) {
      setTestResult({ success: false, message: error instanceof Error ? error.message : String(error) });
    } finally {
      setTesting(false);
    }
  };

  const handleSave = async () => {
    const request = buildRequest();
    if (!request || saving) {
      return;
    }
    setSaving(true);
    setSaveError(null);
    try {
      if (initial) {
        await updateModelConfig(initial.id, request);
      } else {
        await createModelConfig(request);
      }
      onSaved();
    } catch (error) {
      setSaveError(error instanceof Error ? error.message : String(error));
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50" onClick={onClose}>
      <div
        className="w-[480px] rounded-2xl border border-app-border bg-app-panel p-6"
        onClick={(event) => event.stopPropagation()}
      >
        <h3 className="text-base font-medium text-app-text">
          {initial ? "编辑模型配置" : "添加模型配置"}
        </h3>

        <div className="mt-5 flex flex-col gap-4">
          <label className="flex flex-col gap-1.5">
            <span className="text-xs text-app-text-dim">Base URL</span>
            <input
              value={baseUrl}
              onChange={(event) => setBaseUrl(event.target.value)}
              placeholder="https://api.example.com/v1"
              className={cn(INPUT_CLASS, errors.baseUrl && "border-red-500")}
            />
            {errors.baseUrl && <span className="text-xs text-red-500">{errors.baseUrl}</span>}
          </label>

          <label className="flex flex-col gap-1.5">
            <span className="text-xs text-app-text-dim">API Key</span>
            <input
              value={apiKey}
              onChange={(event) => setApiKey(event.target.value)}
              placeholder="留空表示无鉴权网关"
              className={INPUT_CLASS}
            />
          </label>

          <label className="flex flex-col gap-1.5">
            <span className="text-xs text-app-text-dim">模型名称</span>
            <input
              value={modelName}
              onChange={(event) => setModelName(event.target.value)}
              placeholder="glm-5.3-flash"
              className={cn(INPUT_CLASS, errors.modelName && "border-red-500")}
            />
            {errors.modelName && <span className="text-xs text-red-500">{errors.modelName}</span>}
          </label>
        </div>

        {testResult && (
          <p className={cn("mt-3 text-xs", testResult.success ? "text-emerald-500" : "text-red-500")}>
            {testResult.success ? "连接成功" : `连接失败：${testResult.message}`}
          </p>
        )}
        {saveError && <p className="mt-3 text-xs text-red-500">{saveError}</p>}

        <div className="mt-5 flex items-center justify-between">
          <button
            type="button"
            onClick={handleTest}
            disabled={testing}
            className="rounded-lg border border-app-border px-4 py-2 text-sm text-app-text-dim hover:bg-app-panel-hover hover:text-app-text disabled:opacity-60"
          >
            {testing ? "测试中..." : "测试连接"}
          </button>
          <div className="flex gap-2">
            <button
              type="button"
              onClick={onClose}
              className="rounded-lg border border-app-border px-4 py-2 text-sm text-app-text-dim hover:bg-app-panel-hover hover:text-app-text"
            >
              取消
            </button>
            <button
              type="button"
              onClick={handleSave}
              disabled={saving}
              className="rounded-lg bg-app-accent px-4 py-2 text-sm text-white hover:opacity-90 disabled:opacity-60"
            >
              {saving ? "保存中..." : "保存"}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
