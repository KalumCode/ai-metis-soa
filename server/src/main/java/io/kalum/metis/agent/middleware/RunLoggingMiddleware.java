package io.kalum.metis.agent.middleware;

import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.middleware.ActingInput;
import io.agentscope.core.middleware.AgentInput;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.core.middleware.ModelCallInput;
import io.agentscope.core.middleware.ReasoningInput;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 运行链路日志 middleware：AgentScope {@link MiddlewareBase} 的 Metis 融合示例。
 *
 * <p>一次 reply 的完整链路（onAgent 包裹 ReAct 循环，onReasoning 内嵌
 * onSystemPrompt 与 onModelCall，onActing 包裹工具执行），各 hook 做的事：
 *
 * <ul>
 *   <li>onAgent -- 生成 traceId 写入 {@link RuntimeContext}（后续 hook / tool 可经
 *       {@code ctx.get("trace_id")} 读取），记录 reply 开始/结束、耗时与事件数
 *   <li>onReasoning -- 记录每轮推理的上下文消息数
 *   <li>onModelCall -- 记录每次模型 API 调用的模型与耗时
 *   <li>onActing -- 记录每次工具调用的工具名
 *   <li>onSystemPrompt -- Transformer 式注入当前时间，让模型感知实时上下文
 * </ul>
 *
 * <p>注意：middleware 实例被多个 reply 复用，请求级状态只放
 * {@link RuntimeContext} 或方法局部变量，绝不落在实例字段（AgentScope 文档约定）。
 */
public final class RunLoggingMiddleware implements MiddlewareBase {

    private static final Logger log = LoggerFactory.getLogger(RunLoggingMiddleware.class);

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public Flux<AgentEvent> onAgent(
            Agent agent,
            RuntimeContext ctx,
            AgentInput input,
            Function<AgentInput, Flux<AgentEvent>> next) {
        String traceId = UUID.randomUUID().toString().substring(0, 8);
        ctx.put("trace_id", traceId);
        long start = System.nanoTime();
        AtomicLong events = new AtomicLong();

        log.info("[{}] reply 开始 user={} session={} 消息数={}",
                traceId, ctx.getUserId(), ctx.getSessionId(), input.msgs().size());
        return next.apply(input)
                .doOnNext(event -> events.incrementAndGet())
                .doFinally(signal -> log.info("[{}] reply 结束 耗时={}ms 事件数={}",
                        traceId, (System.nanoTime() - start) / 1_000_000, events.get()));
    }

    @Override
    public Flux<AgentEvent> onReasoning(
            Agent agent,
            RuntimeContext ctx,
            ReasoningInput input,
            Function<ReasoningInput, Flux<AgentEvent>> next) {
        int contextSize = input.messages() != null ? input.messages().size() : 0;
        log.info("[{}] 推理开始 上下文消息数={}", ctx.get("trace_id"), contextSize);
        return next.apply(input);
    }

    @Override
    public Flux<AgentEvent> onModelCall(
            Agent agent,
            RuntimeContext ctx,
            ModelCallInput input,
            Function<ModelCallInput, Flux<AgentEvent>> next) {
        long start = System.nanoTime();
        String model = input.model().getClass().getSimpleName();
        return next.apply(input)
                .doFinally(signal -> log.info("[{}] 模型调用 model={} 耗时={}ms",
                        ctx.get("trace_id"), model,
                        (System.nanoTime() - start) / 1_000_000));
    }

    @Override
    public Flux<AgentEvent> onActing(
            Agent agent,
            RuntimeContext ctx,
            ActingInput input,
            Function<ActingInput, Flux<AgentEvent>> next) {
        String tools = input.toolCalls().stream()
                .map(ToolUseBlock::getName)
                .collect(Collectors.joining(", "));
        log.info("[{}] 工具调用 tools={}", ctx.get("trace_id"), tools);
        return next.apply(input);
    }

    @Override
    public Mono<String> onSystemPrompt(Agent agent, RuntimeContext ctx, String currentPrompt) {
        return Mono.just(currentPrompt + "\n\n## 当前时间\n"
                + LocalDateTime.now().format(TIME_FORMAT));
    }
}
