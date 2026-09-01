package io.kalum.metis.agent.middleware;

import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.core.middleware.ModelCallInput;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.kalum.metis.agent.MetisAgentFactory;
import io.kalum.metis.config.MetisProperties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

/**
 * 模型切换 middleware：拦截每次模型 API 调用，按请求指定的模型名转发。
 *
 * <p>chat.send 的 params.model 经 RuntimeContext 传入本次 reply，本 middleware 在
 * {@link #onModelCall} 中读取 {@link #CTX_MODEL_OVERRIDE}，把调用改道到对应名字的
 * {@link Model} 实例。模型实例按名字惰性构建并缓存复用（同一模型名共享一个实例）；
 * base URL 与 API key 仍取 metis.model.* 接入配置，仅模型名随请求切换。
 * 未指定时走默认 agent 模型。
 *
 * <p>实现方式：框架的 modelCallStream 直接使用 {@link ModelCallInput#model()} 发起调用，
 * 因此只需把 input 中的 model 与 options（modelName 覆盖）替换后交给 next，
 * 事件流映射完全由框架完成。
 *
 * <p>注意：middleware 实例被多个 reply 复用，模型缓存是进程级共享状态（只读语义），
 * 请求级模型名只放 {@link RuntimeContext}，不落实例字段。
 */
public final class ModelSwitchMiddleware implements MiddlewareBase {

    /** RuntimeContext key：本次请求要使用的模型名（chat.send params.model）。 */
    public static final String CTX_MODEL_OVERRIDE = "model_override";

    private static final Logger log = LoggerFactory.getLogger(ModelSwitchMiddleware.class);

    private final MetisProperties.Model modelConfig;
    /** 模型名 -> 模型实例（进程级缓存，同一名字共享实例）。 */
    private final ConcurrentMap<String, Model> models = new ConcurrentHashMap<>();

    public ModelSwitchMiddleware(MetisProperties.Model modelConfig) {
        this.modelConfig = modelConfig;
    }

    @Override
    public Flux<AgentEvent> onModelCall(
            Agent agent,
            RuntimeContext ctx,
            ModelCallInput input,
            Function<ModelCallInput, Flux<AgentEvent>> next) {
        Object override = ctx.get(CTX_MODEL_OVERRIDE);
        if (!(override instanceof String modelName) || modelName.isBlank()) {
            return next.apply(input);
        }

        Model target = models.computeIfAbsent(modelName.trim(),
                name -> MetisAgentFactory.buildModelWithName(modelConfig, name));
        GenerateOptions options = GenerateOptions.mergeOptions(
                GenerateOptions.builder().modelName(target.getModelName()).build(),
                input.options());
        log.info("[{}] 模型切换 -> {}", ctx.get("trace_id"), target.getModelName());
        return next.apply(new ModelCallInput(input.messages(), input.tools(), options, target));
    }
}
