---
title: Wiki 内容目录
tags: [index]
status: living
updated: 2026-06-21
owner: architect
---

# Wiki 内容目录

> 本文件是 `docs/` 的**唯一全局目录**。每页一行：`[[link]] 一句话 summary · status · updated`。
> 规范见 [[SCHEMA]]；维护时间线见 [[log]]。新人先看 [[README]]。

## 角色入口

| 你是… | 从这里开始 |
| --- | --- |
| **新贡献者** | [[README]] → [[codemap/backend-modules]] → [[codemap/judging-pipeline]] → 想动的模块 entity 页 |
| **On-call / 运维** | [[ops/arthas-runtime-diagnostics]] → [[refresh-token]]（认证故障）→ [[judge-queue]]（判题卡住） |
| **架构师 / 深挖某子系统** | 对应 [[#实体页]]（总览）+ [[#决策记录]]（为什么）+ [[#概念页]]（横切机制） |
| **前端 / 主题** | [[theme/README]] → [[codemap/frontend-apps]] |
| **LLM 维护者** | [[SCHEMA]]（规则）→ 本目录 → [[log]]（最近改了啥） |

## 按任务索引

- 「判题怎么保证不重复 / 不丢失？」→ [[exactly-once-judging]] + [[0001-judge-outbox-and-generation-fencing]]
- 「沙箱怎么执行用户代码？」→ [[sandbox-d-form]] + [[0002-sandbox-d-form-hexagonal]]
- 「refresh token 存哪、怎么轮换？」→ [[refresh-token]] + [[0003-refresh-token-hash-only-storage]]
- 「比赛评分怎么算 / 虚拟比赛怎么回事？」→ [[contest]] + [[virtual-contest]]
- 「通知怎么保证不重发？」→ [[notification-idempotency]] + [[0004-notification-intent-and-delivery-ledger]]
- 「判题队列怎么从旧路径切到新路径？」→ [[shadow-mode-cutover]]
- 「整个判题链路端到端？」→ [[codemap/judging-pipeline]]
- 「后端有哪些模块？」→ [[codemap/backend-modules]]

---

## 实体页 · `entities/`

- [[submission]] 提交实体：状态机、generation fence、lease、judged 事件 · living · 2026-06-21
- [[contest]] 比赛全景：参与者 / 题目 / 评分 / 排名 / 段位 / virtual · living · 2026-06-21
- [[sandbox-d-form]] D-form 沙箱：四语言 harness、envelope 契约、隔离 · living · 2026-06-21
- [[judge-queue]] 判题队列：hexagonal port/adapter、Redis Streams、outbox、reaper · living · 2026-06-21
- [[refresh-token]] 刷新令牌：hash-only DB、rotate、revoke、HttpOnly cookie · living · 2026-06-21

## 概念页 · `concepts/`

- [[exactly-once-judging]] 判题幂等：outbox 唯一键 + generation fence + lease CAS · living · 2026-06-21
- [[virtual-contest]] 虚拟比赛：session 隔离、rating 不计入正式、cancel/replay · living · 2026-06-21
- [[shadow-mode-cutover]] 影子模式切换：M3a/M3b/M3c 阶段、watermark、comparator · living · 2026-06-21
- [[notification-idempotency]] 通知幂等：intent + delivery ledger、tryClaim 原子 · living · 2026-06-21

## 决策记录 · `decisions/`

> 入口：[[decisions/README]] — 模板、何时写、状态流转

- [[0001-judge-outbox-and-generation-fencing]] 判题为何用 outbox + generation fence · frozen · 2026-06-21
- [[0002-sandbox-d-form-hexagonal]] 沙箱为何选 D-form + hexagonal · frozen · 2026-06-21
- [[0003-refresh-token-hash-only-storage]] refresh token 为何 hash-only DB · frozen · 2026-06-21
- [[0004-notification-intent-and-delivery-ledger]] 通知为何 intent 与 ledger 解耦 · frozen · 2026-06-21

## 架构镜像 · `codemap/`

> 入口：[[codemap/README]] — 镜像约定（首批手写，可由 `ecc:update-codemaps` 接管）

- [[codemap/backend-modules]] 后端 25 模块全景 + 分层 · living · 2026-06-21
- [[codemap/judging-pipeline]] 判题端到端链路 · living · 2026-06-21
- [[codemap/frontend-apps]] 前端 console + management + shared 包 · living · 2026-06-21

## 运维深读 · `ops/`

> 入口：[[ops/README]] — 子目录约定

- [[ops/arthas-runtime-diagnostics]] Arthas MCP 诊断 + 降级路径 · living · 2026-06-21

## 主题专题 · `theme/`

> 入口：[[theme/README]] — 主题系统切面

## 规范与索引

- [[README]] — 着陆：wiki 是什么、怎么用
- [[SCHEMA]] — 唯一规范（三层 / 三动作 / 命名 / frontmatter / 链接）
- [[log]] — 维护时间线（append-only）
