import { useEffect } from "react";

import { ChatPage } from "@/features/chat/chat-page";
import { CapabilityPage } from "@/features/capability/capability-page";
import { ModelConfigPage } from "@/features/model-config/model-config-page";
import { fetchAgents } from "@/lib/api/agent-api";
import { fetchModelConfigs } from "@/lib/api/model-config-api";
import { useAppStore } from "@/store/app-store";
import type { AppTab } from "@/store/app-store";

/**
 * 应用布局：左侧功能 tab 栏 -> 中栏列表 -> 右侧内容区。
 *
 * 参考 nexus 的 AppLayout 三栏结构：功能导航（rail）+ 面板 + 主内容。
 */
export function AppLayout() {
  const tab = useAppStore((s) => s.tab);
  const setTab = useAppStore((s) => s.setTab);
  const setAgents = useAppStore((s) => s.setAgents);
  const setModelConfigs = useAppStore((s) => s.setModelConfigs);

  // 启动时拉取智能体列表与模型配置列表
  useEffect(() => {
    fetchAgents()
      .then(setAgents)
      .catch(() => {
        // 后端未启动时静默，界面展示空列表
      });
    fetchModelConfigs()
      .then(setModelConfigs)
      .catch(() => {
        // 后端未启动时静默，模型选择器只显示"默认模型"
      });
  }, [setAgents, setModelConfigs]);

  return (
    <main className="flex h-full w-full overflow-hidden">
      <FunctionRail tab={tab} onTabChange={setTab} />
      <div className="flex min-w-0 flex-1 overflow-hidden">
        {tab === "chat" ? (
          <ChatPage />
        ) : tab === "model" ? (
          <ModelConfigPage />
        ) : (
          <CapabilityPage />
        )}
      </div>
    </main>
  );
}

function FunctionRail({
  tab,
  onTabChange,
}: {
  tab: AppTab;
  onTabChange: (tab: AppTab) => void;
}) {
  const tabs = [
    { key: "chat" as const, label: "聊天" },
    { key: "model" as const, label: "模型" },
    { key: "capability" as const, label: "能力" },
  ];
  return (
    <nav className="flex w-16 shrink-0 flex-col items-center gap-1 border-r border-app-border bg-app-panel py-4">
      {tabs.map(({ key, label }) => (
        <button
          key={key}
          type="button"
          onClick={() => onTabChange(key)}
          className={
            "flex h-14 w-14 flex-col items-center justify-center gap-1 rounded-xl text-2xs transition-colors " +
            (tab === key
              ? "bg-app-accent-soft text-app-accent"
              : "text-app-text-dim hover:bg-app-panel-hover hover:text-app-text")
          }
        >
          <TabIcon tabKey={key} active={tab === key} />
          <span>{label}</span>
        </button>
      ))}
    </nav>
  );
}

function TabIcon({ tabKey, active }: { tabKey: AppTab; active: boolean }) {
  // 图标用简单几何替代 lucide 之外的自定义资源，保持依赖精简
  const common = "h-5 w-5";
  if (tabKey === "chat") {
    return (
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" className={common}>
        <path
          strokeLinecap="round"
          strokeLinejoin="round"
          d="M8 10h8M8 14h5M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0Z"
          opacity={active ? 1 : 0.8}
        />
      </svg>
    );
  }
  if (tabKey === "model") {
    // 芯片：外方框 + 内小方框 + 四侧引脚
    return (
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" className={common}>
        <path
          strokeLinecap="round"
          strokeLinejoin="round"
          d="M9 2v3M15 2v3M9 19v3M15 19v3M2 9h3M2 15h3M19 9h3M19 15h3M7 7h10v10H7z"
          opacity={active ? 1 : 0.8}
        />
      </svg>
    );
  }
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" className={common}>
      <path
        strokeLinecap="round"
        strokeLinejoin="round"
        d="M13 2 4.5 13.5H11L9.5 22 19 9.5h-6.5L13 2Z"
        opacity={active ? 1 : 0.8}
      />
    </svg>
  );
}
