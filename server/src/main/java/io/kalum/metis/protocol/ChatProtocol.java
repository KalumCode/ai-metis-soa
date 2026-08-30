package io.kalum.metis.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;

/**
 * 桌面端通用通信协议（xYun 协议）。
 *
 * <p>请求信封 {@code type=req}，携带 {@code method}（chat.send / chat.continue / chat.stop）与
 * {@code params}；响应信封 {@code type=res}，以 SSE 逐事件推送 {@code payload}。
 * 出参的 {@code xYunId} 与请求对应；外层 {@code seq} 为本次响应内事件序号，
 * {@code payload.seq} 为单条消息（msgId）内部的事件序号。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class ChatProtocol {

    public static final String TYPE_REQ = "req";
    public static final String TYPE_RES = "res";
    public static final String VERSION_V1 = "v1";
    public static final String EVENT_AGENT = "agent";

    public static final String METHOD_SEND = "chat.send";
    public static final String METHOD_CONTINUE = "chat.continue";
    public static final String METHOD_STOP = "chat.stop";

    /** 响应流类型。 */
    public static final String STREAM_LIFECYCLE = "lifecycle";
    public static final String STREAM_ASSISTANT = "assistant";
    public static final String STREAM_TOOL = "tool";
    public static final String STREAM_ITEM = "item";
    public static final String STREAM_COMMAND_OUTPUT = "command_output";

    /** 响应阶段。 */
    public static final String PHASE_START = "start";
    public static final String PHASE_UPDATE = "update";
    public static final String PHASE_FINAL = "final";

    private ChatProtocol() {}

    /** 通用请求信封。 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ChatRequest(
            String type,
            String xYunId,
            String xYunVersion,
            String method,
            ChatParams params) {}

    /** 请求业务参数。 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ChatParams(
            String sessionId,
            String userId,
            String agent,
            String sourceChannel,
            String message,
            List<Attachment> attachments,
            ContinuationInfo continuationInfo,
            Long requestTime,
            Extra extra) {}

    /** 附件，kind 取值 mail/file/image/note/knowledgeFile/knowledgeBase/expert/cronAdd/skill。 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Attachment(String kind, String refId, String name) {}

    /** 断线续传信息：runId + 已收到的最后事件序号。 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ContinuationInfo(String runId, Long lastSeq) {}

    /** 扩展开关。 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Extra(Boolean enableForceNetworkSearch, Boolean enableModelThinking) {}

    /** 通用响应信封，作为 SSE 单个事件的 data。 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ChatResponse(
            String type, String xYunId, String event, ChatPayload payload, long seq) {}

    /** 响应业务载荷。 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ChatPayload(
            String msgId,
            String sessionKey,
            long seq,
            long timestamp,
            boolean isError,
            String stream,
            String phase,
            String delta,
            String text,
            Map<String, Object> data) {}
}
