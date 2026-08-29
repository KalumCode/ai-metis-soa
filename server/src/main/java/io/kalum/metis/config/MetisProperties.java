package io.kalum.metis.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Metis 服务端配置，绑定 application.yml 中 {@code metis.*} 前缀。
 *
 * @param model        模型接入配置
 * @param agent        HarnessAgent 行为配置
 */
@ConfigurationProperties(prefix = "metis")
public record MetisProperties(Model model, Agent agent) {

    /** 火山方舟（OpenAI 兼容协议）模型接入。 */
    public record Model(
            /** 模型提供方 API base URL，默认火山方舟。 */
            String baseUrl,
            /** 模型名，如 doubao-seed-1-6-250615。 */
            String name,
            /** 读取 API key 的环境变量名。 */
            String apiKeyEnv) {}

    /** Agent 运行参数。 */
    public record Agent(
            /** 工作区根目录，人格（AGENTS.md）与记忆（MEMORY.md）落在这里。 */
            String workspace,
            /** 单轮请求内最大推理迭代数。 */
            Integer maxIters,
            /** 触发上下文压缩的消息条数阈值。 */
            Integer compactionTriggerMessages,
            /** 压缩后保留的近端消息条数。 */
            Integer compactionKeepMessages) {}
}
