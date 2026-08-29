package io.kalum.metis.channel;

import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.SubagentExposedEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.ThinkingBlockDeltaEvent;
import io.agentscope.core.event.ToolCallDeltaEvent;
import io.kalum.metis.protocol.ChatProtocol;
import io.kalum.metis.protocol.ChatProtocol.ChatPayload;
import io.kalum.metis.protocol.ChatProtocol.ChatResponse;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 把 harness 的 {@link AgentEvent} 流映射为 xYun 协议响应事件。
 *
 * <p>每个请求（一次 chat.send）创建一个实例，持有该次运行的映射状态：
 *
 * <ul>
 *   <li>runId -- 本次运行的标识，同时作为 lifecycle 消息的 msgId 与 chat.stop 的定位键
 *   <li>外层 seq -- 本次响应内单调递增的事件序号
 *   <li>payload.seq -- 单条消息（msgId）内部的序号；text -- 该消息的全量累积文本
 * </ul>
 *
 * <p>stream 映射规则：
 *
 * <ul>
 *   <li>lifecycle -- agent 启停、模型调用等运行级事件
 *   <li>assistant -- 文本与思考增量（思考以 {@code data.kind=thinking} 区分）
 *   <li>tool -- 工具调用（TOOL_CALL_*）
 *   <li>command_output -- 工具结果（TOOL_RESULT_*）
 *   <li>item -- 子 agent 暴露（SubagentExposed）
 * </ul>
 */
final class ChatEventMapper {

    private final String xYunId;
    private final String sessionKey;
    private final String runId = UUID.randomUUID().toString();

    private final AtomicLong outerSeq = new AtomicLong();
    private final Map<String, AtomicLong> payloadSeqs = new HashMap<>();
    private final Map<String, StringBuilder> textBuffers = new HashMap<>();

    /**
     * AgentScope 的每个事件 id 相互独立，同一文本/工具块内的 DELTA、END 事件不能直接复用
     * 自身 id 作 msgId，否则增量会各自成消息、text 无法累积。这里按块类型跟踪当前块的
     * msgId（取该块 START 事件的 id）。
     */
    private String currentThinkingMsgId;
    private String currentTextMsgId;
    private String currentToolCallMsgId;
    private String currentToolResultMsgId;

    ChatEventMapper(String xYunId, String sessionKey) {
        this.xYunId = xYunId;
        this.sessionKey = sessionKey;
    }

    String runId() {
        return runId;
    }

    ChatResponse map(AgentEvent event) {
        String type = event.getType().name();
        switch (type) {
            case "AGENT_START" -> {
                return respond(runId, ChatProtocol.STREAM_LIFECYCLE, ChatProtocol.PHASE_START, null,
                        null, null);
            }
            case "AGENT_END" -> {
                return respond(runId, ChatProtocol.STREAM_LIFECYCLE, ChatProtocol.PHASE_FINAL, null,
                        null, null);
            }
            case "THINKING_BLOCK_START" -> {
                currentThinkingMsgId = event.getId();
                return respond(currentThinkingMsgId, ChatProtocol.STREAM_ASSISTANT,
                        ChatProtocol.PHASE_START, null, null, Map.of("kind", "thinking"));
            }
            case "THINKING_BLOCK_DELTA" -> {
                ThinkingBlockDeltaEvent delta = (ThinkingBlockDeltaEvent) event;
                String msgId = requireBlock(currentThinkingMsgId, event);
                String text = accumulate(msgId, delta.getDelta());
                return respond(msgId, ChatProtocol.STREAM_ASSISTANT, ChatProtocol.PHASE_UPDATE,
                        delta.getDelta(), text, Map.of("kind", "thinking"));
            }
            case "THINKING_BLOCK_END" -> {
                String msgId = requireBlock(currentThinkingMsgId, event);
                currentThinkingMsgId = null;
                return respond(msgId, ChatProtocol.STREAM_ASSISTANT, ChatProtocol.PHASE_FINAL, null,
                        snapshot(msgId), Map.of("kind", "thinking"));
            }
            case "TEXT_BLOCK_START" -> {
                currentTextMsgId = event.getId();
                return respond(currentTextMsgId, ChatProtocol.STREAM_ASSISTANT,
                        ChatProtocol.PHASE_START, null, null, Map.of("kind", "text"));
            }
            case "TEXT_BLOCK_DELTA" -> {
                TextBlockDeltaEvent delta = (TextBlockDeltaEvent) event;
                String msgId = requireBlock(currentTextMsgId, event);
                String text = accumulate(msgId, delta.getDelta());
                return respond(msgId, ChatProtocol.STREAM_ASSISTANT, ChatProtocol.PHASE_UPDATE,
                        delta.getDelta(), text, Map.of("kind", "text"));
            }
            case "TEXT_BLOCK_END" -> {
                String msgId = requireBlock(currentTextMsgId, event);
                currentTextMsgId = null;
                return respond(msgId, ChatProtocol.STREAM_ASSISTANT, ChatProtocol.PHASE_FINAL, null,
                        snapshot(msgId), Map.of("kind", "text"));
            }
            case "TOOL_CALL_START" -> {
                currentToolCallMsgId = event.getId();
                return respond(currentToolCallMsgId, ChatProtocol.STREAM_TOOL,
                        ChatProtocol.PHASE_START, null, null, null);
            }
            case "TOOL_CALL_DELTA" -> {
                ToolCallDeltaEvent delta = (ToolCallDeltaEvent) event;
                return respond(requireBlock(currentToolCallMsgId, event),
                        ChatProtocol.STREAM_TOOL, ChatProtocol.PHASE_UPDATE, delta.getDelta(),
                        null, null);
            }
            case "TOOL_CALL_END" -> {
                String msgId = requireBlock(currentToolCallMsgId, event);
                currentToolCallMsgId = null;
                return respond(msgId, ChatProtocol.STREAM_TOOL, ChatProtocol.PHASE_FINAL, null,
                        null, null);
            }
            default -> {
                if (type.startsWith("TOOL_RESULT")) {
                    if (type.endsWith("START")) {
                        currentToolResultMsgId = event.getId();
                    }
                    String msgId = requireBlock(currentToolResultMsgId, event);
                    if (type.endsWith("END")) {
                        currentToolResultMsgId = null;
                    }
                    String phase = type.endsWith("START") ? ChatProtocol.PHASE_START
                            : type.endsWith("END") ? ChatProtocol.PHASE_FINAL
                                    : ChatProtocol.PHASE_UPDATE;
                    return respond(msgId, ChatProtocol.STREAM_COMMAND_OUTPUT, phase, null, null,
                            null);
                }
                if (event instanceof SubagentExposedEvent se) {
                    Map<String, Object> data = new HashMap<>();
                    data.put("subagentId", se.getSubagentId());
                    data.put("agentId", se.getAgentId());
                    data.put("label", se.getLabel());
                    return respond(event.getId(), ChatProtocol.STREAM_ITEM, ChatProtocol.PHASE_START,
                            null, null, data);
                }
                // MODEL_CALL_* / AGENT_RESULT / 其余运行级事件
                return respond(runId, ChatProtocol.STREAM_LIFECYCLE, ChatProtocol.PHASE_UPDATE,
                        null, null, Map.of("source", type));
            }
        }
    }

    /** 构造协议错误事件（isError=true，lifecycle final）。 */
    ChatResponse error(String message) {
        return respond(runId, ChatProtocol.STREAM_LIFECYCLE, ChatProtocol.PHASE_FINAL, null, message,
                null, true);
    }

    /** 构造 stop 确认事件。 */
    ChatResponse stopped() {
        return respond(runId, ChatProtocol.STREAM_LIFECYCLE, ChatProtocol.PHASE_FINAL, null, null,
                Map.of("stopped", true));
    }

    private ChatResponse respond(String msgId, String stream, String phase, String delta,
            String text, Map<String, Object> data) {
        return respond(msgId, stream, phase, delta, text, data, false);
    }

    private ChatResponse respond(String msgId, String stream, String phase, String delta,
            String text, Map<String, Object> data, boolean isError) {
        long seq = payloadSeqs.computeIfAbsent(msgId, k -> new AtomicLong()).incrementAndGet();
        ChatPayload payload =
                new ChatPayload(msgId, sessionKey, seq, System.currentTimeMillis(), isError,
                        stream, phase, delta, text, data);
        return new ChatResponse(ChatProtocol.TYPE_RES, xYunId, ChatProtocol.EVENT_AGENT, payload,
                outerSeq.incrementAndGet());
    }

    /** 累积增量文本并返回全量。 */
    private String accumulate(String msgId, String delta) {
        return textBuffers.computeIfAbsent(msgId, k -> new StringBuilder()).append(delta)
                .toString();
    }

    /** 取当前块的 msgId；流异常（未见 START 先见 DELTA/END）时回退为事件自身 id。 */
    private String requireBlock(String tracked, AgentEvent event) {
        return tracked != null ? tracked : event.getId();
    }

    private String snapshot(String msgId) {
        StringBuilder buffer = textBuffers.get(msgId);
        return buffer != null ? buffer.toString() : null;
    }
}
