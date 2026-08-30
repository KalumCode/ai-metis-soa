package io.kalum.metis.agent.middleware;

import static org.assertj.core.api.Assertions.assertThat;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.middleware.ActingInput;
import io.agentscope.core.middleware.AgentInput;
import io.agentscope.core.middleware.ModelCallInput;
import io.agentscope.core.model.Model;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import reactor.core.publisher.Flux;

/** {@link RunLoggingMiddleware} 各 hook 的行为验证。 */
class RunLoggingMiddlewareTest {

    private final RunLoggingMiddleware middleware = new RunLoggingMiddleware();

    @Test
    void systemPrompt注入当前时间() {
        String result = middleware
                .onSystemPrompt(null, RuntimeContext.empty(), "基础提示词")
                .block();

        assertThat(result).startsWith("基础提示词").contains("## 当前时间");
    }

    @Test
    void onAgent透传事件并写入traceId() {
        RuntimeContext ctx = RuntimeContext.empty();
        AgentEvent event = Mockito.mock(AgentEvent.class);

        var result = middleware
                .onAgent(null, ctx, new AgentInput(List.of()), input -> Flux.just(event))
                .collectList()
                .block();

        assertThat(result).containsExactly(event);
        assertThat((String) ctx.get("trace_id")).isNotBlank();
    }

    @Test
    void onModelCall与onActing透传事件() {
        RuntimeContext ctx = RuntimeContext.empty();
        AgentEvent event = Mockito.mock(AgentEvent.class);
        Model model = Mockito.mock(Model.class);

        var modelResult = middleware
                .onModelCall(null, ctx,
                        new ModelCallInput(List.of(), List.of(), null, model),
                        input -> Flux.just(event))
                .collectList()
                .block();
        var actingResult = middleware
                .onActing(null, ctx, new ActingInput(List.of()), input -> Flux.just(event))
                .collectList()
                .block();

        assertThat(modelResult).containsExactly(event);
        assertThat(actingResult).containsExactly(event);
    }
}
