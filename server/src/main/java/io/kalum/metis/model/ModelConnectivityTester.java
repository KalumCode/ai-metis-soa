package io.kalum.metis.model;

import io.kalum.metis.model.ModelConfigController.ModelConfigRequest;
import io.kalum.metis.model.ModelConfigController.ModelTestResult;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

/**
 * 模型连通性测试：向 OpenAI 兼容网关的 {@code /v1/chat/completions} 发一条最小消息。
 *
 * <p>连通失败属于业务结果，返回 {@code success=false}（HTTP 仍 200），错误信息透传给前端展示；
 * 已知局限：个别网关路径不是 {@code /v1/chat/completions}（如 Azure 风格），不支持，
 * baseUrl 应填到 {@code /v1} 为止。
 */
@Component
public class ModelConnectivityTester {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final int ERROR_MESSAGE_MAX_LENGTH = 200;

    private final WebClient webClient;

    public ModelConnectivityTester() {
        this.webClient = WebClient.create();
    }

    public Mono<ModelTestResult> test(ModelConfigRequest request) {
        String url = resolveChatCompletionsUrl(request.baseUrl());
        WebClient.RequestHeadersSpec<?> spec =
                webClient
                        .post()
                        .uri(url)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(
                                Map.of(
                                        "model", request.modelName(),
                                        "messages", List.of(Map.of("role", "user", "content", "ping")),
                                        "max_tokens", 1,
                                        "stream", false));
        if (request.apiKey() != null && !request.apiKey().isBlank()) {
            spec.header(HttpHeaders.AUTHORIZATION, "Bearer " + request.apiKey());
        }
        return spec
                .retrieve()
                .toBodilessEntity()
                .map(entity -> new ModelTestResult(true, "连接成功"))
                // timeout 放在错误处理之前，超时异常才会流进下方的 onErrorResume
                .timeout(TIMEOUT)
                .onErrorResume(
                        WebClientResponseException.class,
                        e -> {
                            // 4xx/5xx：透传状态码与响应体片段（常含 Invalid API key / model not found 等诊断信息）
                            String body = e.getResponseBodyAsString().trim();
                            String message =
                                    body.isEmpty()
                                            ? "HTTP " + e.getStatusCode().value()
                                            : "HTTP " + e.getStatusCode().value() + ": " + body;
                            return Mono.just(new ModelTestResult(false, truncate(message)));
                        })
                .onErrorResume(WebClientRequestException.class, e -> {
                    String cause = e.getMessage();
                    return Mono.just(new ModelTestResult(false, "无法连接: " + cause));
                })
                .onErrorResume(java.util.concurrent.TimeoutException.class,
                        e -> Mono.just(new ModelTestResult(false, "连接超时（10 秒）")))
                .onErrorResume(e -> {
                    String message = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                    return Mono.just(new ModelTestResult(false, truncate(message)));
                });
    }

    /**
     * URL 规范化：trim、去尾斜杠、缺 /v1 则补齐，最后拼 /chat/completions。
     *
     * <pre>
     * http://192.168.19.172:8888/v1  -> http://192.168.19.172:8888/v1/chat/completions
     * http://192.168.19.172:8888     -> http://192.168.19.172:8888/v1/chat/completions
     * https://api.example.com/v1/    -> https://api.example.com/v1/chat/completions
     * </pre>
     */
    static String resolveChatCompletionsUrl(String baseUrl) {
        String normalized = baseUrl.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (!normalized.endsWith("/v1")) {
            normalized = normalized + "/v1";
        }
        return normalized + "/chat/completions";
    }

    private static String truncate(String message) {
        return message.length() <= ERROR_MESSAGE_MAX_LENGTH
                ? message
                : message.substring(0, ERROR_MESSAGE_MAX_LENGTH) + "...";
    }
}
