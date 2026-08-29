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
  agent?: string;
  sourceChannel?: string;
  message?: string;
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
