package io.kalum.metis.model;

/**
 * 一条模型配置记录：桌面端"模型配置管理"界面维护，持久化在后端配置文件中。
 *
 * <p>apiKey 明文存储与返回（本地单机部署约定）；与 agent 主模型的 metis.model.* 配置相互独立。
 */
public record ModelConfig(String id, String baseUrl, String apiKey, String modelName) {}
