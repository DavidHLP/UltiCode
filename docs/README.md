---
title: UltiCode 文档 Wiki
tags: [index]
status: living
updated: 2026-06-21
owner: architect
---

# UltiCode 文档 Wiki

> `docs/` 是一个由 LLM **增量维护、持续累积、互相链接**的活知识库。它不是「被读的静态文档」，而是把散落在代码、决策、运维里的知识**编译一次、保持最新**的复利产物。
>
> 规范见 [[SCHEMA]]；内容目录见 [[index]]；维护时间线见 [[log]]。

## 这是什么

大多数人对 LLM + 文档的体验像 RAG：上传一堆文件，提问时检索相关片段再生成答案。能用，但 LLM 每次都从零重新发现知识，没有累积。

这里不同。LLM **增量地构建并维护一个持久 wiki**——一组结构化、互链的 markdown 文件。每纳入一个新源，LLM 不只是为以后检索而索引它，而是**读它、提取关键信息、整合进现有 wiki**：更新实体页、修订概念综述、标记新数据与旧结论的矛盾。知识编译一次，然后**保持最新**，而非每次查询重算。

## 三层 / 三动作（一图）

```
                ┌─────────────────────────────────────────────┐
   raw 原始源   │  代码（一等）· 外部文章/gist · 运行现象      │  人类收集
                └─────────────────────────────────────────────┘
                                  │  ingest（纳入 → 综合进 wiki）
                                  ▼
                ┌─────────────────────────────────────────────┐
   wiki 综合产物 │  entities · concepts · decisions            │  LLM 维护
                │  codemap · ops · theme                       │  人类读/提问
                └─────────────────────────────────────────────┘
                                  │  query（先查 wiki 再综合）
                                  │  maintain（链接可解析 / 矛盾标记 / 孤儿审视）
                                  ▼
                ┌─────────────────────────────────────────────┐
   schema 规范  │  SCHEMA.md · index.md · log.md               │  共同演进
                └─────────────────────────────────────────────┘
```

**分工**：你负责**寻源、探索、提问**；LLM 负责**总结、交叉引用、归档、记账**。

## 怎么用

**找一个东西** → 先查 [[index]]（按 category / 按任务），再 drill 进具体页。

**问一个问题** → 直接问；LLM 会先查 wiki（[[index]] → codegraph → ctx_search → backlinks → grep），综合后**带引用**回答。好答案会被归档回 wiki 作为新页。

**纳入一个新源**（代码 PR / 外部文章 / 运行现象）→ 告诉 LLM ingest；它会更新相关页 + [[index]] + [[log]]，并标记矛盾。规范见 [[SCHEMA#2-三动作工作流]]。

## 角色入口

| 你是… | 从这开始 |
| --- | --- |
| 新贡献者 | [[codemap/backend-modules]] → [[codemap/judging-pipeline]] |
| On-call | [[ops/arthas-runtime-diagnostics]] → [[refresh-token]] → [[judge-queue]] |
| 架构师 | [[#五大子系统]] 下的 entity 页 + 对应决策记录 |
| 前端 / 主题 | [[theme/README]] → [[codemap/frontend-apps]] |

## 五大子系统（首批覆盖）

| 子系统 | 实体页 | 概念页 | 决策记录 |
| --- | --- | --- | --- |
| **判题** | [[submission]] · [[judge-queue]] | [[exactly-once-judging]] · [[shadow-mode-cutover]] | [[0001-judge-outbox-and-generation-fencing]] |
| **沙箱** | [[sandbox-d-form]] | — | [[0002-sandbox-d-form-hexagonal]] |
| **比赛** | [[contest]] | [[virtual-contest]] | — |
| **认证** | [[refresh-token]] | — | [[0003-refresh-token-hash-only-storage]] |
| **通知** | — | [[notification-idempotency]] | [[0004-notification-intent-and-delivery-ledger]] |

## 与仓库根文件分工

- [`AGENTS.md`](../AGENTS.md) / [`CLAUDE.md`](../CLAUDE.md)：仓库级权威指南——项目地图、工具链、启动流程、编码风格、运维命令。
- `docs/`（本 vault）：**项目工程知识**——这套代码的架构、运维深读、决策为什么。不重复仓库级规则。

## 参考

- karpathy, *LLM Wiki* — <https://gist.github.com/karpathy/442a6bf555914893e9891c11519de94f>
- [[SCHEMA]] — 唯一规范
- [[index]] — 内容目录
- [[log]] — 维护时间线
