import type { ModelConfig, ModelConfigRequest, ModelTestResult } from "@/types/model-config/model-config";

import { getBackendUrl } from "./backend-url";

/** 拉取模型配置列表。 */
export async function fetchModelConfigs(): Promise<ModelConfig[]> {
  const response = await fetch(`${getBackendUrl()}/api/v1/model-configs`);
  if (!response.ok) {
    throw new Error(`获取模型配置列表失败: ${response.status}`);
  }
  return (await response.json()) as ModelConfig[];
}

/** 新增模型配置，返回服务端生成 id 的完整记录。 */
export async function createModelConfig(request: ModelConfigRequest): Promise<ModelConfig> {
  const response = await fetch(`${getBackendUrl()}/api/v1/model-configs`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(request),
  });
  if (!response.ok) {
    throw new Error(`新增模型配置失败: ${response.status}`);
  }
  return (await response.json()) as ModelConfig;
}

/** 修改模型配置。 */
export async function updateModelConfig(id: string, request: ModelConfigRequest): Promise<ModelConfig> {
  const response = await fetch(`${getBackendUrl()}/api/v1/model-configs/${id}`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(request),
  });
  if (!response.ok) {
    throw new Error(`修改模型配置失败: ${response.status}`);
  }
  return (await response.json()) as ModelConfig;
}

/** 删除模型配置。 */
export async function deleteModelConfig(id: string): Promise<void> {
  const response = await fetch(`${getBackendUrl()}/api/v1/model-configs/${id}`, {
    method: "DELETE",
  });
  if (!response.ok) {
    throw new Error(`删除模型配置失败: ${response.status}`);
  }
}

/** 测试模型连通性（用传入的配置直接测，未保存的草稿也能验证）。 */
export async function testModelConfig(request: ModelConfigRequest): Promise<ModelTestResult> {
  const response = await fetch(`${getBackendUrl()}/api/v1/model-configs/test`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(request),
  });
  if (!response.ok) {
    throw new Error(`测试连接失败: ${response.status}`);
  }
  return (await response.json()) as ModelTestResult;
}
