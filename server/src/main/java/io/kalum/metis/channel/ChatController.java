package io.kalum.metis.channel;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.event.AgentEventType;
import io.agentscope.harness.agent.HarnessAgent;
import io.kalum.metis.agent.MetisAgentFactory;
import io.kalum.metis.agent.middleware.ModelSwitchMiddleware;
import io.kalum.metis.config.MetisProperties;
import io.kalum.metis.model.ModelConfigStore;
import io.kalum.metis.protocol.ChatProtocol;
import io.kalum.metis.protocol.ChatProtocol.ChatRequest;
import io.kalum.metis.protocol.ChatProtocol.ChatResponse;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

/**
 * 桌面端聊天入口：xYun 协议 over SSE。
 *
 * <p>POST JSON 请求信封（type=req，method=chat.send/continue/stop），响应为 SSE 流，
 * 每个事件的 data 是一个 type=res 信封（见 {@link ChatProtocol}）。
 *
 * <p>实现说明：
 *
 * <ul>
 *   <li>chat.send -- 经 {@link HarnessAgent#streamEvents} 驱动 agent，事件经
 *       {@link ChatEventMapper} 映射为协议载荷；同一 (userId, sessionId) 的会话状态由
 *       harness 自动持久化。params.model 为模型配置 id（模型配置管理维护），经 RuntimeContext
 *       传入，按所选配置整体切换接入（baseUrl/apiKey/modelName）。
 *   <li>chat.stop -- 取消该会话当前运行中的事件流（dispose 订阅）。
 *   <li>chat.continue -- 断线续传，需要服务端事件缓冲，暂未实现，返回 isError 事件。
 *   <li>params.agent 路由 -- 当前只有单 agent（metis），暂不参与路由。
 *   <li>params.extra（联网搜索/思考开关）与 attachments -- 已解析，尚未接入业务，保留字段。
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/chat")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String DEFAULT_USER_ID = "10000";

    private final HarnessAgent agent;
    private final ModelConfigStore modelConfigs;

    /** 运行中的会话流：sessionId -> 可取消的订阅。 */
    private final java.util.concurrent.ConcurrentHashMap<String, Disposable> activeRuns =
            new java.util.concurrent.ConcurrentHashMap<>();

    public ChatController(MetisProperties properties, ModelConfigStore modelConfigs) {
        this.agent = MetisAgentFactory.create(properties);
        this.modelConfigs = modelConfigs;
    }

    @PostMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chat(@RequestBody ChatRequest request) {
        validate(request);
        String xYunId = request.xYunId() != null ? request.xYunId() : UUID.randomUUID().toString();
        String sessionId = request.params().sessionId();
        String method = request.method();

        return switch (method) {
            case ChatProtocol.METHOD_SEND -> send(request, xYunId, sessionId);
            case ChatProtocol.METHOD_STOP -> stop(xYunId, sessionId);
            case ChatProtocol.METHOD_CONTINUE -> unsupported(xYunId, sessionId,
                    "chat.continue 断线续传暂未实现");
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "不支持的 method: " + method);
        };
    }

    private Flux<ServerSentEvent<String>> send(ChatRequest request, String xYunId,
            String sessionId) {
        String message = request.params().message();
        if (message == null || message.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "params.message 不能为空");
        }

        String userId = resolveUserId(request);
        // params.model 按请求切换模型：经 RuntimeContext 传入，由 ModelSwitchMiddleware 拦截模型调用
        io.agentscope.core.agent.RuntimeContext.Builder ctxBuilder =
                sessionId != null && !sessionId.isBlank()
                        ? io.agentscope.core.agent.RuntimeContext.builder()
                                .userId(userId).sessionId(sessionId)
                        : io.agentscope.core.agent.RuntimeContext.builder().userId(userId);
        // params.model 为模型配置 id：解析为完整接入参数（baseUrl/apiKey/modelName）经
        // RuntimeContext 传入，由 ModelSwitchMiddleware 拦截模型调用整体切换；
        // 配置不存在时回退默认模型（可用性优先，不打断对话）
        String model = request.params().model();
        if (model != null && !model.isBlank()) {
            modelConfigs.get(model.trim()).ifPresentOrElse(
                    config -> ctxBuilder.put(ModelSwitchMiddleware.CTX_MODEL_OVERRIDE,
                            new ModelSwitchMiddleware.ModelOverride(
                                    config.baseUrl(), config.apiKey(), config.modelName())),
                    () -> log.warn("模型配置不存在，回退默认模型: {}", model.trim()));
        }

        io.agentscope.core.message.Msg userMsg = io.agentscope.core.message.Msg.builder()
                .role(io.agentscope.core.message.MsgRole.USER)
                .textContent(message)
                .build();

        ChatEventMapper mapper = new ChatEventMapper(xYunId, sessionId);
        Sinks.Many<ServerSentEvent<String>> sink =
                Sinks.many().unicast().onBackpressureBuffer();

        Disposable run = agent
                .streamEvents(java.util.List.of(userMsg), ctxBuilder.build())
                .map(mapper::map)
                .map(ChatController::toSse)
                .subscribe(
                        event -> sink.tryEmitNext(event),
                        error -> {
                            sink.tryEmitNext(toSse(mapper.error(error.getMessage())));
                            sink.tryEmitComplete();
                        },
                        () -> sink.tryEmitComplete());

        // 同一会话同时只保留一个运行中的流，后到的覆盖先到的
        Disposable previous = sessionId != null ? activeRuns.put(sessionId, run) : null;
        if (previous != null) {
            previous.dispose();
        }

        return sink.asFlux().doFinally(signal -> {
            run.dispose();
            if (sessionId != null) {
                activeRuns.remove(sessionId, run);
            }
        });

        //AgentEventType
    }

    private Flux<ServerSentEvent<String>> stop(String xYunId, String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "chat.stop 需要 sessionId");
        }
        ChatEventMapper mapper = new ChatEventMapper(xYunId, sessionId);
        Disposable run = activeRuns.remove(sessionId);
        if (run != null) {
            run.dispose();
        }
        return Flux.just(toSse(mapper.stopped()));
    }

    private Flux<ServerSentEvent<String>> unsupported(String xYunId, String sessionId,
            String message) {
        ChatEventMapper mapper = new ChatEventMapper(xYunId, sessionId);
        return Flux.just(toSse(mapper.error(message)));
    }

    private static String resolveUserId(ChatRequest request) {
        String userId = request.params().userId();
        return userId != null && !userId.isBlank() ? userId : DEFAULT_USER_ID;
    }

    private static void validate(ChatRequest request) {
        if (request.params() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "缺少 params");
        }
        if (request.method() == null || request.method().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "缺少 method");
        }
        if (request.type() != null && !ChatProtocol.TYPE_REQ.equals(request.type())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "type 必须为 req，收到: " + request.type());
        }
        if (request.xYunVersion() != null && !ChatProtocol.VERSION_V1
                .equals(request.xYunVersion())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "不支持的 xYunVersion: " + request.xYunVersion());
        }
    }

    private static ServerSentEvent<String> toSse(ChatResponse response) {
        return ServerSentEvent.<String>builder()
                .event(ChatProtocol.EVENT_AGENT)
                .data(toJson(response))
                .build();
    }

    private static String toJson(Object value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return "{\"type\":\"res\",\"payload\":{\"isError\":true,"
                    + "\"stream\":\"lifecycle\",\"phase\":\"final\","
                    + "\"text\":\"响应序列化失败\"}}";
        }
    }
}
