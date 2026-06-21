---
title: 架构镜像（Codemap）
tags: [mirror, architecture]
status: living
updated: 2026-06-21
owner: architect
---

# 架构镜像（Codemap）

> 记录代码**现在长什么样**（区别于 `decisions/` 记「为什么」、`entities/` 记「某实体综合认知」）。
>
> ⚠️ 本目录首批为**手写基线**（标记 `mirror: 手写`）。日常维护可由 `ecc:update-codemaps` 从源码重新生成接管；生成器运行后，以生成内容为准并刷新本页 `updated:`。

## 现有镜像

- [[backend-modules]] — 后端 25 模块全景 + 分层 + 非模块包
- [[judging-pipeline]] — 判题端到端链路（提交 → 队列 → outbox → worker → 沙箱 → 判决 → 事件）
- [[frontend-apps]] — console + management 双前端 + shared 7 包

## 实时符号图

需要「X 调用谁 / 改 X 会断什么 / X 定义在哪」这类结构化问题，直接用 codegraph MCP（`codegraph_context` / `codegraph_trace` / `codegraph_impact`）——它比本镜像更实时（文件 watcher ~500ms 落后写）。本镜像的价值在**人类可读的叙事性总览**，codegraph 的价值在**精确的符号级查询**。

## 与其他目录分工

| 问题 | 去哪 |
| --- | --- |
| 代码现在长什么样？ | 本目录 |
| 某实体（提交/比赛）综合认知？ | [[index]] → entities |
| 为什么这么设计？ | [[index]] → decisions |
| 某符号的调用链 / 影响面？ | codegraph MCP |
