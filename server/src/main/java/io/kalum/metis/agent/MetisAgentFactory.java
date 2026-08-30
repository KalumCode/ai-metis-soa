package io.kalum.metis.agent;

import io.agentscope.core.model.Model;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import io.agentscope.extensions.model.openai.formatter.OpenAIChatFormatter;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.memory.compaction.CompactionConfig;
import io.kalum.metis.agent.middleware.RunLoggingMiddleware;
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

        return HarnessAgent.builder()
                .name("metis")
                .model(buildModel(modelConfig))
                .sysPrompt(MetisSystemPrompt.build())
                .middleware(new RunLoggingMiddleware())
                .workspace(resolveWorkspace(agentConfig))
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
