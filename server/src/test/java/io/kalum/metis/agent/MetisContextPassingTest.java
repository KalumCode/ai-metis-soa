package io.kalum.metis.agent;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.middleware.ActingInput;
import io.agentscope.core.tool.ToolCallParam;
import io.agentscope.core.tool.Toolkit;
import io.kalum.metis.agent.middleware.RunLoggingMiddleware;
import io.kalum.metis.agent.tools.MetisTools;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

/**
 * 验证"工具写 RuntimeContext、hook 读"的上下文传递机制。
 *
 * <p>走框架真实执行路径：{@code Toolkit.callTool} 触发 {@code set_context_value}
 * 的 @Tool 方法，RuntimeContext 由 ToolMethodInvoker 注入（非模型参数）；
 * {@link RunLoggingMiddleware#onActing} 在工具执行完成后从同一 ctx 实例读取并打印。
 * 唯一没覆盖的是"模型决定调用该工具"这一步（需真实模型）。
 */
class MetisContextPassingTest {

    private static final String CONTEXT_VALUE = "来自工具的问候-20260904";

    private ListAppender<ILoggingEvent> logAppender;
    private Logger hookLogger;

    @BeforeEach
    void captureHookLogs() {
        logAppender = new ListAppender<>();
        logAppender.start();
        hookLogger = (Logger) LoggerFactory.getLogger(RunLoggingMiddleware.class);
        hookLogger.addAppender(logAppender);
    }

    @AfterEach
    void detachAppender() {
        hookLogger.detachAppender(logAppender);
    }

    @Test
    void toolWritesContextAndActingHookReadsIt() {
        RuntimeContext ctx = RuntimeContext.builder().userId("u1").sessionId("ctx-demo-test").build();
        Toolkit toolkit = MetisAgentFactory.buildToolkit(Path.of(".metis/workspace"));
        RunLoggingMiddleware middleware = new RunLoggingMiddleware();

        ToolUseBlock toolCall =
                new ToolUseBlock(
                        "call-1",
                        "set_context_value",
                        Map.of("value", CONTEXT_VALUE),
                        "{\"value\":\"" + CONTEXT_VALUE + "\"}",
                        null);

        // onActing 的 next 模拟 acting 阶段：框架经 Toolkit.callTool 执行工具，
        // 与 middleware 共享同一个 ctx 实例
        ToolResultBlock[] result = new ToolResultBlock[1];
        middleware
                .onActing(
                        null,
                        ctx,
                        new ActingInput(List.of(toolCall)),
                        input -> toolkit
                                .callTool(
                                        ToolCallParam.builder()
                                                .toolUseBlock(input.toolCalls().get(0))
                                                .runtimeContext(ctx)
                                                .build())
                                .doOnNext(r -> result[0] = r)
                                .flatMapMany(r -> Flux.<AgentEvent>empty()))
                .blockLast();

        // 1. 工具执行成功且框架确实注入了 RuntimeContext（返回写入确认）
        assertThat(result[0]).isNotNull();
        assertThat(result[0].getOutput()).isNotNull();
        assertThat(String.valueOf(result[0].getOutput())).contains("已写入上下文").contains(CONTEXT_VALUE);

        // 2. 工具写入的值在同一个 ctx 实例上可见
        assertThat(ctx.<String>get(MetisTools.CTX_CONTEXT_VALUE)).isEqualTo(CONTEXT_VALUE);

        // 3. onActing hook 在工具执行完成后读取到该值并打印了日志
        assertThat(logAppender.list)
                .anySatisfy(event -> {
                    assertThat(event.getFormattedMessage())
                            .contains("hook 读取到工具写入的上下文")
                            .contains(MetisTools.CTX_CONTEXT_VALUE)
                            .contains(CONTEXT_VALUE);
                });
    }
}
