# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

**Metis (ai-metis-soa)** 是面向企业、科研团队及开发者的多智能体协作平台：支持创建可独立命名的 AI Agent，每个智能体具备专属隔离工作区与运行时托管的文件式持久记忆，支撑长链路任务执行、多轮迭代协作与复杂业务编排。

本工程处于初始骨架阶段（目录已建、代码为空），架构参考本地工程 `E:\Team\java\other\nexus`（Go 后端多智能体平台，后端语言不同但分层思想、文档组织可借鉴）。

## 技术栈与架构规划

| 部分 | 技术 | 位置 |
| --- | --- | --- |
| 后端 | Java 17 + AgentScope（多智能体框架） | `server/` |
| 桌面端 | React + TypeScript + Vite + pnpm | `desktop/` |
| 文档 | Markdown，中文为主 | `docs/` |
| 维护脚本 | — | `scripts/` |

关键决策（已定）：

- **纯桌面版客户端**，不做 Web 版。客户端与后端通过 HTTP + WebSocket 通信，后端独立部署，无进程内嵌方案。
- 后端参考 nexus 的分层模式：接口层（handler/controller）→ 业务层（service）→ 持久层（repository），按领域垂直分包（agent、session、orchestration、memory 等），一个领域贯穿三层。
- 前端参考 nexus 的 `web/src/` 结构：`features/`（业务功能垂直切分）、`pages/`（路由页面）、`store/`、`hooks/`、`lib/`、`shared/`。因无 Web 版，多入口与浏览器兼容相关结构不采用。
- 文档组织参考 nexus 的 `docs/`：`README.md` 作总索引，`specs/` 一个模块一份 `*-spec.md`，另有 `guides/`、`operations/`、`testing/`。文档只描述已存在的行为，链接代码真相源，提案类内容放 issue/PR 不进 docs。

## 常用命令

后端（Java 17 + Maven，规划中，骨架尚未搭建）与桌面端（Vite + pnpm）的构建、测试、运行命令将在对应模块初始化后补充到此处。当前仓库无可运行的构建命令。

## 分支约定

默认分支 `main`，功能开发在 `feature/YYYYMM` 命名的分支上进行。

## Git 工作流（.claude/commands）

提交代码使用自定义命令，不要手动拆步骤执行：

- `/commit-push`：`git diff --stat` 查看变更 → 生成 commit message 并提交 → 推送到远程分支
- `/commit-pr`：同上流程，额外创建 Pull Request，标题基于 commit 内容，PR 描述须包含变更摘要

MCP 服务：已启用 `mysql-mcp-server` 与 `stitch`（见 `.claude/settings.json`）。
