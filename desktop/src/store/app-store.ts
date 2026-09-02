import { create } from "zustand";
import { persist } from "zustand/middleware";

import type { AgentInfo } from "@/types/agent/agent-info";
import type { ModelConfig } from "@/types/model-config/model-config";

/** 左侧功能 tab。 */
export type AppTab = "chat" | "capability" | "model";

/** 一条对话消息。 */
export interface ChatMessage {
  /** 客户端生成的消息 id（用户消息 UUID / 助手消息取协议 msgId）。 */
  id: string;
  role: "user" | "assistant";
  content: string;
  /** 思考过程文本（助手消息才有）。 */
  thinking?: string;
  timestamp: number;
  /** 生成中的消息不可编辑且展示光标。 */
  pending?: boolean;
  /** 生成失败的错误信息。 */
  error?: string;
}

/** 一个会话。 */
export interface ChatSession {
  id: string;
  title: string;
  /** 关联的 agent id。 */
  agentId: string;
  messages: ChatMessage[];
  createdAt: number;
  updatedAt: number;
}

interface AppStore {
  /** 当前功能 tab。 */
  tab: AppTab;
  setTab: (tab: AppTab) => void;

  /** 智能体列表（来自 GET /api/v1/agents）。 */
  agents: AgentInfo[];
  setAgents: (agents: AgentInfo[]) => void;

  /** 模型配置列表（来自 GET /api/v1/model-configs），不持久化。 */
  modelConfigs: ModelConfig[];
  /** 写入模型配置列表；选中配置已不存在时自动回退默认模型。 */
  setModelConfigs: (configs: ModelConfig[]) => void;

  /** 当前选中的模型配置 id；null = 服务端默认模型。全局共享，随 persist 持久化。 */
  selectedModelId: string | null;
  setSelectedModelId: (id: string | null) => void;

  /** 会话，按 updatedAt 倒序展示。 */
  sessions: ChatSession[];
  /** 当前选中会话 id。 */
  activeSessionId: string | null;
  selectSession: (sessionId: string | null) => void;

  /** 新建会话并选中，返回会话 id。 */
  createSession: (agentId: string) => string;
  /** 追加一条消息（或就地更新已有消息）。 */
  upsertMessage: (sessionId: string, message: ChatMessage) => void;
  /** 就地修改某条消息的字段（流式更新用）。 */
  patchMessage: (
    sessionId: string,
    messageId: string,
    patch: Partial<ChatMessage>,
  ) => void;
  /** 删除会话。 */
  removeSession: (sessionId: string) => void;
}

export const useAppStore = create<AppStore>()(
  persist(
    (set) => ({
      tab: "chat",
      setTab: (tab) => set({ tab }),

      agents: [],
      setAgents: (agents) => set({ agents }),

      modelConfigs: [],
      setModelConfigs: (configs) =>
        set((state) => ({
          modelConfigs: configs,
          // 选中配置被删除时回退默认模型
          selectedModelId:
            state.selectedModelId && configs.some((c) => c.id === state.selectedModelId)
              ? state.selectedModelId
              : null,
        })),

      selectedModelId: null,
      setSelectedModelId: (id) => set({ selectedModelId: id }),

      sessions: [],
      activeSessionId: null,
      selectSession: (sessionId) => set({ activeSessionId: sessionId }),

      createSession: (agentId) => {
        const id = crypto.randomUUID();
        const now = Date.now();
        const session: ChatSession = {
          id,
          title: "新对话",
          agentId,
          messages: [],
          createdAt: now,
          updatedAt: now,
        };
        set((state) => ({
          sessions: [session, ...state.sessions],
          activeSessionId: id,
        }));
        return id;
      },

      upsertMessage: (sessionId, message) =>
        set((state) => ({
          sessions: state.sessions.map((session) => {
            if (session.id !== sessionId) {
              return session;
            }
            const exists = session.messages.some((m) => m.id === message.id);
            const messages = exists
              ? session.messages.map((m) => (m.id === message.id ? message : m))
              : [...session.messages, message];
            // 首条用户消息作为会话标题
            const title =
              session.title === "新对话" && message.role === "user"
                ? message.content.slice(0, 30)
                : session.title;
            return {
              ...session,
              title,
              messages,
              updatedAt: Date.now(),
            };
          }),
        })),

      patchMessage: (sessionId, messageId, patch) =>
        set((state) => ({
          sessions: state.sessions.map((session) => {
            if (session.id !== sessionId) {
              return session;
            }
            return {
              ...session,
              messages: session.messages.map((m) =>
                m.id === messageId ? { ...m, ...patch } : m,
              ),
              updatedAt: Date.now(),
            };
          }),
        })),

      removeSession: (sessionId) =>
        set((state) => {
          const sessions = state.sessions.filter((s) => s.id !== sessionId);
          return {
            sessions,
            activeSessionId:
              state.activeSessionId === sessionId
                ? (sessions[0]?.id ?? null)
                : state.activeSessionId,
          };
        }),
    }),
    {
      name: "metis-chat-history",
      partialize: (state) => ({
        sessions: state.sessions,
        activeSessionId: state.activeSessionId,
        selectedModelId: state.selectedModelId,
      }),
    },
  ),
);

/** 当前选中的会话。 */
export function useActiveSession(): ChatSession | null {
  const sessions = useAppStore((s) => s.sessions);
  const activeSessionId = useAppStore((s) => s.activeSessionId);
  return sessions.find((s) => s.id === activeSessionId) ?? null;
}

/** 按更新时间倒序的会话列表。 */
export function useSortedSessions(): ChatSession[] {
  const sessions = useAppStore((s) => s.sessions);
  return [...sessions].sort((a, b) => b.updatedAt - a.updatedAt);
}

/** 当前会话的流式发送状态（跨组件共享的运行控制）。 */
interface RunningState {
  runningSessionIds: Record<string, true>;
  startRunning: (sessionId: string) => void;
  stopRunning: (sessionId: string) => void;
}

export const useRunningStore = create<RunningState>((set) => ({
  runningSessionIds: {},
  startRunning: (sessionId) =>
    set((state) => ({
      runningSessionIds: { ...state.runningSessionIds, [sessionId]: true },
    })),
  stopRunning: (sessionId) =>
    set((state) => {
      const next = { ...state.runningSessionIds };
      delete next[sessionId];
      return { runningSessionIds: next };
    }),
}));
