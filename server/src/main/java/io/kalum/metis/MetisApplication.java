package io.kalum.metis;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Metis 后端入口。
 *
 * <p>基于 AgentScope 2.0 {@code HarnessAgent} 装配多智能体协作平台，通过 ChatUiChannel
 * 对桌面端暴露 SSE 流式接口。
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class MetisApplication {

    public static void main(String[] args) {
        SpringApplication.run(MetisApplication.class, args);
    }
}
