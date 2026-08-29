# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

**Metis (ai-metis-soa)** 是面向企业、科研团队及开发者的多智能体协作平台：支持创建可独立命名的 AI Agent，每个智能体具备专属隔离工作区与运行时托管的文件式持久记忆，支撑长链路任务执行、多轮迭代协作与复杂业务编排。

后端骨架已搭建（Spring Boot + AgentScope Harness），桌面端尚未初始化。架构参考本地工程 `E:\Team\java\other\nexus`（Go 后端多智能体平台，后端语言不同但分层思想、文档组织可借鉴）；AgentScope 2.0 框架源码与文档见本地 `E:\Team\java\other\agentscope-java`（harness 文档：`docs/v2/zh/docs/harness/`）。

## 技术栈与架构规划

| 部分 | 技术 | 位置 |
| --- | --- | --- |
| 后端 | Java 17 + Spring Boot 4.0.4（WebFlux）+ AgentScope 2.0 Harness，模型走火山方舟（OpenAI 兼容协议） | `server/` |
| 桌面端 | React + TypeScript + Vite + pnpm | `desktop/` |
| 文档 | Markdown，中文为主 | `docs/` |
| 维护脚本 | — | `scripts/` |

关键决策（已定）：

- **后端单模块起步**：AgentScope harness 已内置会话持久化、双层长期记忆、子 agent 编排、工作区、上下文压缩，业务层不重复实现这些；等业务成型再考虑拆 Maven 模块。HarnessAgent 装配入口为 `server/src/main/java/io/kalum/metis/agent/MetisAgentFactory.java`，对外 SSE 接口为 `channel/ChatController.java`。
- **纯桌面版客户端**，不做 Web 版。客户端与后端通过 HTTP + SSE 通信，后端独立部署，无进程内嵌方案。
- 后端参考 nexus 的分层模式：接口层（handler/controller）→ 业务层（service）→ 持久层（repository），按领域垂直分包（agent、session、orchestration、memory 等），一个领域贯穿三层。
- 前端参考 nexus 的 `web/src/` 结构：`features/`（业务功能垂直切分）、`pages/`（路由页面）、`store/`、`hooks/`、`lib/`、`shared/`。因无 Web 版，多入口与浏览器兼容相关结构不采用。
- 文档组织参考 nexus 的 `docs/`：`README.md` 作总索引，`specs/` 一个模块一份 `*-spec.md`，另有 `guides/`、`operations/`、`testing/`。文档只描述已存在的行为，链接代码真相源，提案类内容放 issue/PR 不进 docs。

## 常用命令

后端位于 `server/`（单模块 Spring Boot 工程）：

```bash
cd server
mvn compile                              # 编译
mvn spring-boot:run                      # 启动（需先设置 ARK_API_KEY 环境变量）
mvn test                                 # 测试
```

启动后验证 SSE 流式接口（需 ARK_API_KEY）：

```bash
curl -N -X POST "http://localhost:8080/api/v1/chat" \
  -H "Content-Type: application/json" -H "Accept: text/event-stream" \
  -d '{"type":"req","xYunId":"<uuid>","xYunVersion":"v1","method":"chat.send","params":{"sessionId":"s1","sourceChannel":"601","message":"你好"}}'
```

接口为 xYun 通用协议（`server/src/main/java/io/kalum/metis/protocol/ChatProtocol.java` 是协议唯一真相源）：POST JSON 请求信封（method=chat.send/continue/stop），响应为 SSE，每个事件 data 是 type=res 信封。`chat.continue`（断线续传）暂未实现，返回 isError 事件。

模型接火山方舟（OpenAI 兼容协议），配置见 `server/src/main/resources/application.yml` 的 `metis.*`（base URL、模型名、API key 环境变量名均可覆盖）。agent 运行时数据落在 `server/.metis/`（已 gitignore）。

桌面端（React + TS + Vite + pnpm 渲染层 + WPF/WebView2 Windows 壳）：

```bash
cd desktop
pnpm install && pnpm dev      # 开发模式（Vite 代理到后端 8080）
pnpm typecheck && pnpm build  # 类型检查 / 构建 dist

# Windows 桌面壳（需 .NET 8 SDK + WebView2 Runtime）
powershell -ExecutionPolicy Bypass -File desktop/windows/build.ps1
# 产物: desktop/windows/.build/app/Metis/Metis.exe
```

桌面壳架构：壳只负责窗口/标题栏/配置，渲染层为 `desktop/dist`（构建时拷入 `Resources/webdist`，经 WebView2 虚拟主机 `https://app.local` 加载）。后端地址来自壳注入的 `window.__METIS_CONFIG__.backendUrl`（%APPDATA%/Metis/config.json，默认 `http://127.0.0.1:8080`），渲染层统一经 `src/lib/api/backend-url.ts` 的 `getBackendUrl()` 取值。后端 CORS 允许 `https://app.local`（`server/.../config/WebCorsConfig.java`）。

## 分支约定

默认分支 `main`，功能开发在 `feature/YYYYMM` 命名的分支上进行。

## Git 工作流（.claude/commands）

提交代码使用自定义命令，不要手动拆步骤执行：

- `/commit-push`：`git diff --stat` 查看变更 → 生成 commit message 并提交 → 推送到远程分支
- `/commit-pr`：同上流程，额外创建 Pull Request，标题基于 commit 内容，PR 描述须包含变更摘要

MCP 服务：已启用 `mysql-mcp-server` 与 `stitch`（见 `.claude/settings.json`）。
