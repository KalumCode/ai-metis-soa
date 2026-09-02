/** 一条模型配置记录，对应后端 io.kalum.metis.model.ModelConfig（apiKey 明文往返，本地单机部署约定）。 */
export interface ModelConfig {
  id: string;
  baseUrl: string;
  apiKey: string;
  modelName: string;
}

/** 新增 / 修改 / 测试共用的请求体，对应后端 ModelConfigController.ModelConfigRequest。 */
export interface ModelConfigRequest {
  baseUrl: string;
  apiKey: string;
  modelName: string;
}

/** 连通性测试结果，对应后端 ModelConfigController.ModelTestResult（连通失败也返回 200）。 */
export interface ModelTestResult {
  success: boolean;
  message: string;
}
