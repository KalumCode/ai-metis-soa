package io.kalum.metis.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.agentscope.core.permission.PermissionBehavior;
import io.agentscope.core.permission.PermissionContextState;
import io.agentscope.core.permission.PermissionDecision;
import io.agentscope.core.permission.PermissionEngine;
import io.agentscope.core.permission.PermissionMode;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolBase;
import io.agentscope.core.tool.ToolParam;
import io.agentscope.core.tool.Toolkit;
import io.kalum.metis.agent.tools.MetisTools;
import java.nio.file.Files;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 权限系统实例：验证 MetisAgentFactory 装配的权限上下文真的会拦截工具调用。
 *
 * <p>三个场景对应文档中的三种决策：
 *
 * <ol>
 *   <li>DENY -- shell 工具 {@code execute} 命中 deny 规则，BYPASS 模式下也拦截（不可绕过）
 *   <li>ALLOW -- 只读工具 {@code list_workspace_files} 命中 allow 规则
 *   <li>DONT_ASK 兜底 -- 切到 DONT_ASK 模式后，未命中任何规则的工具被静默拒绝
 * </ol>
 */
class MetisPermissionTest {

    @TempDir
    java.nio.file.Path workspace;

    /** 模拟 harness 的 shell 执行工具（同名即命中 deny 规则）。 */
    static class DummyShellTool {
        @Tool(name = "execute", description = "Execute a shell command")
        public String execute(@ToolParam(name = "command", description = "命令") String command) {
            return "executed: " + command;
        }
    }

    /** 不在白名单里的工具（用于 DONT_ASK 兜底验证）。 */
    static class UnlistedTool {
        @Tool(name = "unlisted_tool", description = "A tool not covered by any rule")
        public String run() {
            return "ok";
        }
    }

    @Test
    void shellCommandShouldBeDeniedEvenInBypassMode() {
        PermissionDecision decision = check("execute", Map.of("command", "rm -rf /"), null);
        assertEquals(PermissionBehavior.DENY, decision.getBehavior());
    }

    @Test
    void readOnlyToolShouldBeAllowed() {
        PermissionDecision decision =
                check("list_workspace_files", Map.of("sub_path", ""), null);
        assertEquals(PermissionBehavior.ALLOW, decision.getBehavior());
    }

    @Test
    void unlistedToolShouldBeDeniedUnderDontAskMode() {
        PermissionDecision decision =
                check("unlisted_tool", Map.of(), PermissionMode.DONT_ASK);
        assertEquals(PermissionBehavior.DENY, decision.getBehavior());
    }

    /** 在给定模式下（null 用默认 BYPASS）评估指定工具调用的权限决策。 */
    private PermissionDecision check(
            String toolName, Map<String, Object> input, PermissionMode modeOverride) {
        PermissionContextState context = MetisAgentFactory.buildPermissionContext(null);
        if (modeOverride != null) {
            context = context.withMode(modeOverride);
        }
        try {
            Files.writeString(workspace.resolve("AGENTS.md"), "# Metis\n");
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(new MetisTools(workspace));
        toolkit.registerTool(new DummyShellTool());
        toolkit.registerTool(new UnlistedTool());

        ToolBase tool = (ToolBase) toolkit.getTool(toolName);
        return new PermissionEngine(context).checkPermission(tool, input).block();
    }
}
