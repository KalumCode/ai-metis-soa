/** xYun 协议请求信封（见 server ChatProtocol.java）。 */
export interface ChatRequest {
  type: "req";
  xYunId: string;
  xYunVersion: "v1";
  method: "chat.send" | "chat.continue" | "chat.stop";
  params: ChatParams;
}

export interface ChatParams {
  sessionId: string;
  /** 用户 ID，用于后端取用户信息（未传时后端兜底为 10000）。 */
  userId?: string;
  agent?: string;
  sourceChannel?: string;
  message?: string;
  /** 模型配置 id（GET /api/v1/model-configs）；不传时使用服务端默认模型。 */
  model?: string;
  requestTime?: number;
}

/** xYun 协议响应信封。 */
export interface ChatResponse {
  type: "res";
  xYunId: string;
  event: string;
  payload: ChatPayload;
  seq: number;
}

export interface ChatPayload {
  msgId: string;
  sessionKey: string;
  seq: number;
  timestamp: number;
  isError: boolean;
  /** lifecycle | assistant | tool | item | command_output */
  stream: string;
  /** start | update | final */
  phase: string;
  delta?: string;
  text?: string;
  data?: { kind?: string; stopped?: boolean; [key: string]: unknown };
}
