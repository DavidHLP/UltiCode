---
title: CODEMAPS — 架构快照索引
tags: [index, architecture, reference, living]
status: living
updated: 2026-06-19
owner: architect
generator: ecc:update-codemaps
---

# CODEMAPS — 架构快照索引

> **读者**：所有需要"代码现在长什么样"的快速、可链接答案的工程师。
> 本目录是**自动生成**的架构快照，不是手写文档 — 见下文「重生成节奏」。

## 这是什么

`CODEMAPS/` 存放从**源码**重新生成的架构快照：拓扑、模块地图、schema、依赖、沙箱。
数据活在代码里；这里只是渲染结果，方便被其它文档（ADR、runbook、主题文档）稳定地链接引用。
每份文件的 `<!-- Generated: ... -->` 头标注了生成时间与扫描范围。

> **手写 vs 生成**：本 README 是**唯一**手写文件 — 它是这个目录的索引笔记（MOC）。
> 其余 6 份 `.md` 全部由 `ecc:update-codemaps` 生成；**不要手改它们的正文**。

## 索引

| 快照                       | 作用                                                           | 链入场景                              |
| -------------------------- | -------------------------------------------------------------- | ------------------------------------- |
| [[architecture]]           | 系统拓扑、PM2 进程、数据流、安全边界、ADR 镜像、迁移策略       | 架构总览入口                          |
| [[backend]]                | Spring Boot 分层、26 个业务模块地图、安全/websocket 包          | 新增后端模块 / 排查后端归属           |
| [[frontend]]               | console / management / shared 包布局、路由、store、API 客户端  | 前端跨端协作 / shared 包改动          |
| [[data]]                   | MySQL schema 分组、Flyway 迁移索引、表来源                     | 新增迁移 / 排查表关系                 |
| [[dependencies]]           | 三方库、Docker 基础设施、CI 矩阵                               | 升级依赖 / 排查端口与镜像归属         |
| [[sandbox]]                | OJ 沙箱三段构建管线、verdict 编解码、语言 manifest             | 改评测 / 加新语言（见 [[0002-sandbox-hexagonal-dform]]） |

## 重生成节奏

| 节奏     | 操作                                                          |
| -------- | ------------------------------------------------------------- |
| 每周     | `ecc:update-codemaps` 从源码重新生成本目录 6 份快照（自动）   |
| 触到源码 | 若本次 PR 改了模块边界 / schema / 依赖，**同 PR** 重跑生成器  |
| 季度     | 手工核对本 README 索引与生成器输出是否一致，修正偏差          |

## 关于 frontmatter（重生成注意）

本目录的 6 份快照现已带 `frontmatter`（`title / tags / status / updated / owner / generator`），
**但这些 frontmatter 必须由生成器在重生成时保留**。如果你在维护 `ecc:update-codemaps`：

- 生成器在写每份 `.md` 时，应先写 `---\n…\n---` frontmatter 再写 `<!-- Generated: -->` 头与正文。
- 若生成器不输出 frontmatter，下一次重生成会**清空本次手工补充**，所有快照在图谱里会变成孤岛。

> 这是一个已知的后续 TODO：把 frontmatter 输出固化进 `ecc:update-codemaps`。

## 参见

- [[README|工程文档首页]]
- [[0002-sandbox-hexagonal-dform]] — 沙箱六边形重构（D-form），[[sandbox]] 的决策来源
- [[0003-queue-outbox-fencing]]、[[0004-notification-intents-ledger]] — outbox/账本模式，[[backend]] 数据流段引用
- [[architecture]] §Architecture Decisions — ADR 镜像（与 [[README|ADR 索引]] 同 PR 同步）
