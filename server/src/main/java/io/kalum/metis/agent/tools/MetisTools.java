package io.kalum.metis.agent.tools;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Stream;

/**
 * Metis 智能体内置的自定义工具集。
 *
 * <p>通过 {@code Toolkit.registerTool(new MetisTools(...))} 注册到 agent，
 * 每个 {@code @Tool} 注解方法对模型暴露为一个可调用工具。工具随代码发布，
 * 属于工程模板的一部分：重装 / 重建工作区后依然默认初始化。
 */
public final class MetisTools {

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 工具向 {@link RuntimeContext} 写入上下文数据的演示键。
     *
     * <p>{@code set_context_value} 工具写入，{@code RunLoggingMiddleware.onActing}
     * 在工具执行完成后读取并打印——两者拿到的是同一次 reply 的同一个 ctx 实例。
     */
    public static final String CTX_CONTEXT_VALUE = "metis.context_value";

    private final Path workspaceRoot;

    public MetisTools(Path workspaceRoot) {
        this.workspaceRoot = workspaceRoot.toAbsolutePath().normalize();
    }

    /**
     * 查询指定时区的当前时间。
     *
     * @param timezone IANA 时区名，如 "Asia/Shanghai"；为空时用系统默认时区
     * @return 形如 "当前时间（Asia/Shanghai）：2026-08-30 12:00:00" 的字符串
     */
    @Tool(name = "get_current_time", description = "获取指定时区的当前时间，时区为空则用系统默认")
    public String getCurrentTime(
            @ToolParam(name = "timezone", description = "IANA 时区名，如 'Asia/Shanghai'，可留空")
                    String timezone) {
        try {
            ZoneId zone =
                    (timezone == null || timezone.isBlank())
                            ? ZoneId.systemDefault()
                            : ZoneId.of(timezone.trim());
            return "当前时间（" + zone.getId() + "）："
                    + LocalDateTime.now(zone).format(TIME_FORMAT);
        } catch (Exception e) {
            return "无效的时区名: " + timezone + "，示例：'Asia/Shanghai'、'America/New_York'";
        }
    }

    /**
     * 列出工作区内指定子目录（默认根目录）下的文件与目录清单。
     *
     * @param subPath 工作区相对子路径，如 "memory" 或 ""（根目录）
     * @return 每行一个条目的清单文本
     */
    @Tool(name = "list_workspace_files", description = "列出 agent 工作区内指定子路径（默认根目录）下的文件与目录")
    public String listWorkspaceFiles(
            @ToolParam(name = "sub_path", description = "工作区相对子路径，如 'memory'；留空表示根目录")
                    String subPath) {
        Path dir = resolveWithinWorkspace(subPath);
        if (!Files.exists(dir)) {
            return "路径不存在: " + (subPath == null || subPath.isBlank() ? "." : subPath);
        }
        if (!Files.isDirectory(dir)) {
            return subPath + " 是文件而非目录";
        }
        try (Stream<Path> entries = Files.list(dir)) {
            List<String> lines =
                    entries.map(p -> (Files.isDirectory(p) ? "[目录] " : "[文件] ") + p.getFileName())
                            .sorted()
                            .toList();
            if (lines.isEmpty()) {
                return "目录为空: " + dir;
            }
            return "工作区 " + workspaceRoot.relativize(dir) + " 下的条目：\n"
                    + String.join("\n", lines);
        } catch (IOException e) {
            return "读取目录失败: " + e.getMessage();
        }
    }

    /**
     * 把一条上下文数据写入当前会话的 {@link RuntimeContext}。
     *
     * <p>演示"工具写、hook 读"的上下文传递机制：{@code ctx} 参数不带 {@code @ToolParam}
     * 注解，由框架从当前 reply 的 RuntimeContext 自动注入（模型不感知、不需要传），
     * 工具内 {@code ctx.put(...)} 写入的值对同一 reply 内的 middleware hook 可见——
     * {@code RunLoggingMiddleware.onActing} 会在工具执行完成后读取并打印。
     *
     * @param value 要写入的上下文数据内容
     * @param ctx 框架注入的当前调用运行时上下文（非模型参数）
     * @return 写入结果说明
     */
    @Tool(name = "set_context_value",
            description = "把一条上下文数据写入当前会话运行时（RuntimeContext），供运行链路 hook 读取")
    public String setContextValue(
            @ToolParam(name = "value", description = "要写入的上下文数据内容") String value,
            RuntimeContext ctx) {
        if (ctx == null) {
            return "当前调用没有可用的 RuntimeContext，写入失败";
        }
        if (value == null || value.isBlank()) {
            return "value 不能为空";
        }
        ctx.put(CTX_CONTEXT_VALUE, value.trim());
        return "已写入上下文（key=" + CTX_CONTEXT_VALUE + "）: " + value.trim();
    }

    /** 把工作区相对路径解析为受限在工作区内的绝对路径，拦截越界访问。 */
    private Path resolveWithinWorkspace(String subPath) {
        Path dir = workspaceRoot;
        if (subPath != null && !subPath.isBlank()) {
            dir = workspaceRoot.resolve(subPath.trim()).normalize();
        }
        if (!dir.startsWith(workspaceRoot)) {
            throw new IllegalArgumentException("路径越出工作区: " + subPath);
        }
        return dir;
    }
}
