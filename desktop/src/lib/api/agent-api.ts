import type { AgentInfo } from "@/types/agent/agent-info";

import { getBackendUrl } from "./backend-url";

/** 拉取智能体列表。 */
export async function fetchAgents(): Promise<AgentInfo[]> {
  const response = await fetch(`${getBackendUrl()}/api/v1/agents`);
  if (!response.ok) {
    throw new Error(`获取智能体列表失败: ${response.status}`);
  }
  return (await response.json()) as AgentInfo[];
}
