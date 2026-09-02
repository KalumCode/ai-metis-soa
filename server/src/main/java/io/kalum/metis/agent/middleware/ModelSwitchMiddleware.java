package io.kalum.metis.agent.middleware;

import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.core.middleware.ModelCallInput;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.kalum.metis.agent.MetisAgentFactory;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

/**
 * 模型切换 middleware：拦截每次模型 API 调用，按请求指定的模型配置转发。
 *
 * <p>chat.send 的 params.model（模型配置 id）由 ChatController 解析为
 * {@link ModelOverride} 经 RuntimeContext 传入本次 reply，本 middleware 在
 * {@link #onModelCall} 中读取 {@link #CTX_MODEL_OVERRIDE}，把调用改道到对应
 * {@link Model} 实例——baseUrl / apiKey / modelName 全部取自所选模型配置。
 * 未指定时走默认 agent 模型（metis.model.*）。
 *
 * <p>实现方式：框架的 modelCallStream 直接使用 {@link ModelCallInput#model()} 发起调用，
 * 因此只需把 input 中的 model 与 options（modelName 覆盖）替换后交给 next，
 * 事件流映射完全由框架完成。
 *
 * <p>注意：middleware 实例被多个 reply 复用，模型缓存是进程级共享状态（只读语义）；
 * 缓存 key 取接入三元组内容（baseUrl|modelName|apiKey），配置被修改后 key 变化、
 * 自然构建新实例，旧条目闲置等 GC（配置量小，无需淘汰）。请求级信息只放
 * {@link RuntimeContext}，不落实例字段。
 */
public final class ModelSwitchMiddleware implements MiddlewareBase {

    /** RuntimeContext key：本次请求使用的完整模型配置（chat.send params.model 解析结果）。 */
    public static final String CTX_MODEL_OVERRIDE = "model_override";

    /** 本次请求的完整模型覆盖：整体切换 baseUrl/apiKey/modelName。 */
    public record ModelOverride(String baseUrl, String apiKey, String modelName) {}

    private static final Logger log = LoggerFactory.getLogger(ModelSwitchMiddleware.class);

    /** 接入三元组内容 key -> 模型实例（进程级缓存，同一接入参数共享实例）。 */
    private final ConcurrentMap<String, Model> models = new ConcurrentHashMap<>();

    @Override
    public Flux<AgentEvent> onModelCall(
            Agent agent,
            RuntimeContext ctx,
            ModelCallInput input,
            Function<ModelCallInput, Flux<AgentEvent>> next) {
        if (!(ctx.get(CTX_MODEL_OVERRIDE) instanceof ModelOverride override)) {
            return next.apply(input);
        }

        String key = override.baseUrl() + "|" + override.modelName() + "|" + override.apiKey();
        Model target = models.computeIfAbsent(key,
                k -> MetisAgentFactory.buildModel(
                        override.baseUrl(), override.apiKey(), override.modelName()));
        GenerateOptions options = GenerateOptions.mergeOptions(
                GenerateOptions.builder().modelName(target.getModelName()).build(),
                input.options());
        log.info("[{}] 模型切换 -> {} | {}", ctx.get("trace_id"),
                override.baseUrl(), target.getModelName());
        return next.apply(new ModelCallInput(input.messages(), input.tools(), options, target));
    }
}
