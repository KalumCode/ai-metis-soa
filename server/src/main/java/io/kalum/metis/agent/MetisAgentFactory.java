package io.kalum.metis.agent;

import io.agentscope.core.model.Model;
import io.agentscope.core.permission.PermissionBehavior;
import io.agentscope.core.permission.PermissionContextState;
import io.agentscope.core.permission.PermissionMode;
import io.agentscope.core.permission.PermissionRule;
import io.agentscope.core.skill.repository.ClasspathSkillRepository;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import io.agentscope.extensions.model.openai.formatter.OpenAIChatFormatter;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.memory.compaction.CompactionConfig;
import io.kalum.metis.agent.middleware.RunLoggingMiddleware;
import io.kalum.metis.agent.tools.MetisTools;
import io.kalum.metis.config.MetisProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

/**
 * Metis 主智能体装配工厂。
 *
 * <p>把 {@link HarnessAgent} 的工程能力按 Metis 的需求接线：
 *
 * <ul>
 *   <li>模型：火山方舟（OpenAI 兼容协议），base URL 与模型名来自配置
 *   <li>工作区：人格（AGENTS.md）与长期记忆（MEMORY.md）的落盘位置
 *   <li>技能：classpath 技能仓库（{@code resources/skills/}），随 JAR 发布，
 *       重装 / 重建工作区后依然默认初始化
 *   <li>工具：{@link MetisTools}（当前时间、工作区文件清单）注册到 Toolkit
 *   <li>权限：{@link #buildPermissionContext} 拦截工具调用（deny 规则不可绕过）
 *   <li>上下文压缩：超过阈值自动摘要，保证上下文有界
 *   <li>middleware：{@link io.kalum.metis.agent.middleware.RunLoggingMiddleware}
 *       输出运行链路日志（reply / 推理 / 模型调用 / 工具调用）并注入当前时间
 * </ul>
 */
public final class MetisAgentFactory {

    private static final String DEFAULT_BASE_URL = "http://192.168.19.172:8888/v1";
    private static final String DEFAULT_API_KEY_ENV = "ARK_API_KEY";
    private static final String DEFAULT_WORKSPACE = ".metis/workspace";
    private static final String DEFAULT_MODEL = "glm-5.3-flash";

    private MetisAgentFactory() {}

    public static HarnessAgent create(MetisProperties properties) {
        MetisProperties.Model modelConfig = properties.model();
        MetisProperties.Agent agentConfig = properties.agent();

        Path workspace = resolveWorkspace(agentConfig);

        return HarnessAgent.builder()
                .name("metis")
                .model(buildModel(modelConfig))
                .sysPrompt(MetisSystemPrompt.build())
                .middleware(new RunLoggingMiddleware())
                .workspace(workspace)
                .toolkit(buildToolkit(workspace))
                .skillRepository(buildSkillRepository())
                .permissionContext(buildPermissionContext(agentConfig))
                .maxIters(resolveMaxIters(agentConfig))
                .compaction(
                        CompactionConfig.builder()
                                .triggerMessages(
                                        orDefault(
                                                agentConfig.compactionTriggerMessages(), 30))
                                .keepMessages(
                                        orDefault(agentConfig.compactionKeepMessages(), 10))
                                .build())
                .build();
    }

    /** 构建 Toolkit：注册 Metis 自定义工具，harness 内置工具在 build 时一并注册。 */
    static Toolkit buildToolkit(Path workspace) {
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(new MetisTools(workspace));
        return toolkit;
    }

    /** 构建 classpath 技能仓库：读取 JAR 内 resources/skills/ 下的技能目录。 */
    static ClasspathSkillRepository buildSkillRepository() {
        try {
            return new ClasspathSkillRepository("skills");
        } catch (java.io.IOException e) {
            throw new IllegalStateException("初始化 classpath 技能仓库失败: resources/skills", e);
        }
    }

    /**
     * 构建权限上下文：拦截 agent 的每一次工具调用。
     *
     * <p>规则语义（评估顺序，先命中先生效）：
     *
     * <ol>
     *   <li>deny 规则 -- 一律拒绝，任何模式（含 BYPASS）下都不可绕过
     *   <li>ask 规则 -- 暂停等待用户确认（当前 SSE 通道无确认 UI，暂不配置）
     *   <li>allow 规则 -- 直接放行
     *   <li>mode 兜底 -- BYPASS 放行其余调用；DONT_ASK 拒绝其余调用
     * </ol>
     *
     * <p>默认策略（mode 可经 metis.agent.permission-mode 覆盖）：
     *
     * <ul>
     *   <li>deny {@code execute} -- agent 在宿主机上执行任意 shell 命令风险过高，一律拒绝；
     *       这条规则即"权限实例"：换成 BYPASS 模式也照样拦截
     *   <li>allow 只读/低风险工具 -- 当前在 BYPASS 下是冗余的，但把 mode 切到
     *       DONT_ASK 后即变成白名单：只有这些工具会执行，其余静默拒绝
     * </ul>
     */
    static PermissionContextState buildPermissionContext(MetisProperties.Agent config) {
        PermissionContextState.Builder builder =
                PermissionContextState.builder().mode(resolvePermissionMode(config));

        // deny：禁止 agent 执行 shell 命令（不可绕过）
        builder.addDenyRule(
                "execute",
                new PermissionRule("execute", null, PermissionBehavior.DENY, "projectSettings"));

        // allow：白名单（DONT_ASK 模式下生效；BYPASS 下仅为声明）
        for (String tool : new String[] {
            "read_file", "list_files", "glob_files", "grep_files",
            "write_file", "edit_file",
            "memory_search", "memory_get", "memory_save",
            "session_search", "session_history", "session_list",
            "load_skill_through_path", "todo_write",
            "get_current_time", "list_workspace_files"
        }) {
            builder.addAllowRule(
                    tool, new PermissionRule(tool, null, PermissionBehavior.ALLOW, "projectSettings"));
        }
        return builder.build();
    }

    /** 解析权限模式配置（metis.agent.permission-mode），默认 BYPASS。 */
    static PermissionMode resolvePermissionMode(MetisProperties.Agent config) {
        String raw = config != null ? config.permissionMode() : null;
        if (raw == null || raw.isBlank()) {
            return PermissionMode.BYPASS;
        }
        try {
            return PermissionMode.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "无效的 metis.agent.permission-mode: "
                            + raw
                            + "（可选 DEFAULT / ACCEPT_EDITS / EXPLORE / BYPASS / DONT_ASK）",
                    e);
        }
    }

    /** 构建火山方舟模型（OpenAI 兼容协议）。 */
    static Model buildModel(MetisProperties.Model config) {
        String baseUrl = config != null && notBlank(config.baseUrl())
                ? config.baseUrl()
                : DEFAULT_BASE_URL;
        String modelName =
                config != null && notBlank(config.name()) ? config.name() : DEFAULT_MODEL;
        String apiKeyEnv =
                config != null && notBlank(config.apiKeyEnv())
                        ? config.apiKeyEnv()
                        : DEFAULT_API_KEY_ENV;

        String apiKey = System.getenv(apiKeyEnv);
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "缺少模型 API key：请设置环境变量 " + apiKeyEnv + "（配置项 metis.model.api-key-env）");
        }

        return OpenAIChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(modelName)
                .stream(true)
                .formatter(new OpenAIChatFormatter())
                .build();
    }

    private static Path resolveWorkspace(MetisProperties.Agent config) {
        String workspace = config != null && notBlank(config.workspace())
                ? config.workspace()
                : DEFAULT_WORKSPACE;
        Path path = Paths.get(workspace).toAbsolutePath().normalize();
        try {
            Files.createDirectories(path);
            seedWorkspaceTemplate(path);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("无法创建 agent 工作区目录: " + path, e);
        }
        return path;
    }

    /** 首次启动时把 classpath:workspace/AGENTS.md 播种到工作区（已存在则不覆盖）。 */
    private static void seedWorkspaceTemplate(Path workspace) throws java.io.IOException {
        Path target = workspace.resolve("AGENTS.md");
        if (Files.exists(target)) {
            return;
        }
        try (var in = MetisAgentFactory.class.getResourceAsStream("/workspace/AGENTS.md")) {
            if (in != null) {
                Files.copy(in, target);
            }
        }
    }

    private static int resolveMaxIters(MetisProperties.Agent config) {
        Integer maxIters = config != null ? config.maxIters() : null;
        return maxIters != null ? maxIters : 25;
    }

    private static int orDefault(Integer value, int fallback) {
        return Objects.requireNonNullElse(value, fallback);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
