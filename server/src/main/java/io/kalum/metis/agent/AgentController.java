package io.kalum.metis.agent;

import io.kalum.metis.config.MetisProperties;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 智能体列表接口：供桌面端中栏展示。
 *
 * <p>当前平台装配单 agent（metis 主智能体）；未来多 agent 时在此扩展注册机制。
 */
@RestController
@RequestMapping("/api/v1/agents")
public class AgentController {

    private final MetisProperties properties;

    public AgentController(MetisProperties properties) {
        this.properties = properties;
    }

    @GetMapping
    public List<AgentInfo> list() {
        return List.of(
                new AgentInfo(
                        "metis",
                        "Metis",
                        "主智能体：理解意图、规划任务并调度子代理执行"));
    }

    /** 智能体概要信息。 */
    public record AgentInfo(String id, String name, String description) {}
}
