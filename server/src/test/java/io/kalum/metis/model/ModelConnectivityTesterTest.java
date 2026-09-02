package io.kalum.metis.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * ModelConnectivityTester 的 URL 规范化表驱动验证（真实连通验证走接口手工测试）。
 */
class ModelConnectivityTesterTest {

    @Test
    void resolveChatCompletionsUrlShouldNormalizeBaseUrl() {
        String[][] cases = {
            // 带斜杠结尾的 /v1
            {"http://192.168.19.172:8888/v1", "http://192.168.19.172:8888/v1/chat/completions"},
            // 不带 /v1：自动补齐
            {"http://192.168.19.172:8888", "http://192.168.19.172:8888/v1/chat/completions"},
            // 尾部多个斜杠
            {"http://192.168.19.172:8888/v1//", "http://192.168.19.172:8888/v1/chat/completions"},
            // https + 域名
            {"https://api.example.com", "https://api.example.com/v1/chat/completions"},
            {"https://api.example.com/v1/", "https://api.example.com/v1/chat/completions"},
            // 首尾空白
            {"  http://localhost:8888/v1  ", "http://localhost:8888/v1/chat/completions"},
        };
        for (String[] c : cases) {
            assertEquals(c[1], ModelConnectivityTester.resolveChatCompletionsUrl(c[0]), "输入: " + c[0]);
        }
    }
}
