import { Bot } from "lucide-react";

import { ConversationView } from "@/features/chat/conversation-view";
import { useSortedSessions, useAppStore, useActiveSession } from "@/store/app-store";
import { cn } from "@/shared/ui/class-name";

/** 聊天页：中栏智能体列表 + 会话列表，右侧对话区。 */
export function ChatPage() {
  const agents = useAppStore((s) => s.agents);
  const activeSession = useActiveSession();
  const sessions = useSortedSessions();
  const createSession = useAppStore((s) => s.createSession);
  const selectSession = useAppStore((s) => s.selectSession);

  const activeAgent = agents.find((a) => a.id === activeSession?.agentId) ?? agents[0];

  return (
    <>
      {/* 中栏：智能体 + 会话列表 */}
      <aside className="flex w-64 shrink-0 flex-col border-r border-app-border bg-app-panel">
        <div className="px-4 pt-4 pb-2 text-xs font-medium text-app-text-faint">智能体</div>
        <div className="px-2">
          {agents.length === 0 ? (
            <div className="px-2 py-3 text-xs text-app-text-faint">后端未连接</div>
          ) : (
            agents.map((agent) => (
              <button
                key={agent.id}
                type="button"
                onClick={() => {
                  // 选中该 agent 的最新会话，或新建
                  const existing = sessions.find((s) => s.agentId === agent.id);
                  if (existing) {
                    selectSession(existing.id);
                  } else {
                    createSession(agent.id);
                  }
                }}
                className={cn(
                  "flex w-full items-center gap-3 rounded-lg px-2 py-2 text-left transition-colors",
                  activeAgent?.id === agent.id
                    ? "bg-app-accent-soft"
                    : "hover:bg-app-panel-hover",
                )}
              >
                <span className="flex h-9 w-9 items-center justify-center rounded-lg bg-app-accent/20 text-app-accent">
                  <Bot className="h-5 w-5" />
                </span>
                <span className="min-w-0">
                  <span className="block truncate text-sm text-app-text">{agent.name}</span>
                  <span className="block truncate text-xs text-app-text-faint">
                    {agent.description}
                  </span>
                </span>
              </button>
            ))
          )}
        </div>

        <div className="mt-4 flex items-center justify-between px-4 pb-2">
          <span className="text-xs font-medium text-app-text-faint">历史会话</span>
          {activeAgent ? (
            <button
              type="button"
              onClick={() => createSession(activeAgent.id)}
              className="rounded-md px-2 py-0.5 text-xs text-app-accent hover:bg-app-accent-soft"
            >
              + 新对话
            </button>
          ) : null}
        </div>
        <div className="min-h-0 flex-1 overflow-y-auto px-2 pb-3">
          {sessions.map((session) => (
            <button
              key={session.id}
              type="button"
              onClick={() => selectSession(session.id)}
              className={cn(
                "mb-0.5 w-full rounded-lg px-3 py-2 text-left transition-colors",
                session.id === activeSession?.id
                  ? "bg-app-panel-hover"
                  : "hover:bg-app-panel-hover/60",
              )}
            >
              <span className="block truncate text-sm text-app-text">{session.title}</span>
              <span className="block text-xs text-app-text-faint">
                {new Date(session.updatedAt).toLocaleString("zh-CN", {
                  month: "numeric",
                  day: "numeric",
                  hour: "2-digit",
                  minute: "2-digit",
                })}
              </span>
            </button>
          ))}
          {sessions.length === 0 ? (
            <div className="px-3 py-3 text-xs text-app-text-faint">暂无会话</div>
          ) : null}
        </div>
      </aside>

      {/* 右栏：对话区 */}
      <section className="flex min-w-0 flex-1 flex-col">
        {activeSession ? (
          <ConversationView key={activeSession.id} sessionId={activeSession.id} />
        ) : (
          <EmptyHint />
        )}
      </section>
    </>
  );
}

function EmptyHint() {
  const agents = useAppStore((s) => s.agents);
  const createSession = useAppStore((s) => s.createSession);
  return (
    <div className="flex h-full items-center justify-center">
      <div className="text-center">
        <p className="text-sm text-app-text-dim">选择或新建一个对话开始</p>
        {agents[0] ? (
          <button
            type="button"
            onClick={() => createSession(agents[0].id)}
            className="mt-3 rounded-lg bg-app-accent px-4 py-2 text-sm text-white hover:opacity-90"
          >
            新建对话
          </button>
        ) : null}
      </div>
    </div>
  );
}
