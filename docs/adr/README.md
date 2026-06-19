---
title: 架构决策记录（ADR）
tags: [adr, index, governance]
status: living
updated: 2026-06-19
owner: architect
adr_count: 12
---

# 架构决策记录（ADR）

> **目的**：记录一个不那么显然的技术决策背后的**原因**、考虑过的备选、以及我们接受的权衡。
> ADR 一旦接受就**不可变** — 被替代的决策会有一篇新 ADR 链回旧 ADR。

## 何时该写一篇 ADR

在以下情况写一篇：

- **不那么显然** — 未来的读者会有冲动去撤销它
- **横切多端** — 影响 ≥ 2 个模块或前端
- **难以回退** — schema 变更、协议不兼容、供应商锁定
- **代码里有记录** — 但**原因**活在代码之外（迁移里的注释不够）

以下情况**不要**写 ADR：

- 风格选择（交给 `.editorconfig` / `springboot-rules.md`）
- 模块级约定（`CLAUDE.md` 已经覆盖）
- Bug 修复（commit message + `RUNBOOK.md` §4 就够）

## 状态生命周期

```
Proposed → Accepted → (Superseded | Deprecated | Reverted)
                  └─ Rejected（少见；留作记录）
```

| 状态       | 含义                                                          |
| ------------ | ---------------------------------------------------------------- |
| `proposed`   | 讨论中；尚未生效                                          |
| `accepted`   | 决策已生效                                             |
| `superseded` | 被另一篇 ADR 取代（在 `Supersedes` / `Superseded by` 里链回） |
| `deprecated` | 不再推荐，但保留供上下文                                |
| `reverted`   | 决策被撤销（少见；记录教训）                   |

## 编号

- 顺序 4 位编号，零填充（`0001`、`0002`、…）
- 一旦分配就**永不重用**（即使被拒绝）
- 文件名格式：`NNNN-kebab-case-slug.md`（与 H1 标题对齐）
- 把关联的决策放一组：`0005-…` 后接 `0005a-…`（同一部署手册的回滚演练，见 `0005-rolling-deploy-rollback.md`）

## 文件模板

复制到新 `NNNN-slug.md` 并填每一节。如果某节真的不适用，写 `N/A` 加一行原因。

```markdown
---
title: <一句话总结，会作为 H1>
tags: [adr, <子系统>, <状态>]
status: proposed | accepted | superseded | deprecated | reverted
updated: YYYY-MM-DD
date: YYYY-MM-DD
deciders: <姓名或角色>
supersedes: <ADR-NNNN 或 N/A>
superseded_by: <ADR-NNNN 或 N/A>
---

# NNNN — <决策标题>

## 背景

<什么触发了这个决策？有哪些约束？手头有什么证据/数据？最多 1-2 段。>

## 决策

<一句话讲明选择。然后用祈使语气（"我们将…"）扩成一段。>

## 备选方案

1. **<备选 A>** — <不选的原因，1-2 行>
2. **<备选 B>** — <不选的原因，1-2 行>
3. **<备选 C>** — <不选的原因，1-2 行>

## 影响

**正面** — <收益 1>、<收益 2>

**负面** — <代价 1>、<代价 2>

**运维影响** — <部署、监控、on-call 有什么变化>

## 参考

- **迁移**：`init-db/migrations/V<ts>__...sql`
- **代码**：`backend-spring/src/main/java/com/ulticode/modules/<x>/...`
- **CODEMAPS**：`docs/CODEMAPS/<area>.md` §<节>
- **相关 ADR**：`NNNN-…`、`NNNN-…`
- **外部**：<RFC、博客、厂商文档 — 仅在确实引用时列出>
```

## 索引

| ADR     | 标题                                                 | 状态   | 日期       |
| ------- | ----------------------------------------------------- | -------- | ---------- |
| [[0001-verdict-status-codec|0001]] | 评测状态码（沙箱 ↔ 后端）              | accepted | 2026-05-xx |
| [[0002-sandbox-hexagonal-dform|0002]] | 沙箱六边形重构（D-form）                  | accepted | 2026-05-xx |
| [[0003-queue-outbox-fencing|0003]] | 评测 outbox 围栏（`judge_outbox`、lease） | accepted | 2026-06-13 |
| [[0004-notification-intents-ledger|0004]] | 通知 intents + 投递账本                | accepted | 2026-06-13 |
| [[0005-rolling-deploy-rollback|0005]] | 滚动部署 + 回滚演练                        | accepted | 2026-06-xx |
| [[0005a-rollback-drill|0005a]] | 回滚演练自动化                              | accepted | 2026-06-xx |
| [[0006-contest-scoring-activation|0006]] | 比赛评分引擎激活                     | accepted | 2026-06-17 |
| [[0007-virtual-contest-rating-isolation|0007]] | 虚拟赛生命周期 + 等级隔离          | accepted | 2026-06-17 |
| [[0008-websocket-cookie-auth|0008]] | WebSocket 鉴权（仅 cookie，禁 query token）          | accepted | 2026-06-xx |
| [[0009-israted-gate|0009]] | `isRated` 门控 + 虚拟等级隔离             | accepted | 2026-06-xx |
| [[0010-cancel-state-virtual-replay|0010]] | 取消态 + 虚拟回放边界                | accepted | 2026-06-xx |
| [[0011-crit6-shadow-mode|0011]] | CRIT-6 shadow mode 评估                         | proposed | 2026-06-xx |
| [[0012-shared-auth-ui-extraction|0012]] | 抽取 auth UI 组件与 view shell 到 shared/auth-ui | accepted | 2026-06-19 |

> **状态说明**：上表是权威索引。`CODEMAPS/architecture.md` §"Architecture Decisions" 是镜像 — **同一个 PR** 里同步更新两边。
>
> **日期说明**：标 `2026-xx-xx` / `2026-06-xx` 的条目是决策接受时的精确日**尚未回填**（占位符，诚实表示"未知"）。知悉真实日期者请把 `date:` frontmatter 与本表同步补全为 `YYYY-MM-DD` —— **不要编造日期**（ADR 不可变，历史日期是事实）。

## 编写工作流

1. 以 `proposed` 起稿，PR 上加 `area: adr` 标签
2. 由 `architect` 代理 + 相关领域评审者评审（如后端决策用 `java-reviewer`、鉴权/沙箱决策用 `security-reviewer`）
3. 合入后改 `status: accepted`
4. 在 `CODEMAPS/architecture.md` §"Architecture Decisions" 镜像这条记录
5. 在代码注释、runbook、PR 描述里通过**完整文件名**引用 ADR — 永远不要只写编号（文件名比编号更耐久）

## 反模式

- **"活文档"式 ADR** — ADR 不可变。需求变了就写一篇新的 supersede 旧的，不要就地编辑旧的。
- **空白小节** — 模板里每一节都有它的理由。真的不适用就写 `N/A` + 原因，绝不写 `TBD`。
- **把决策埋在散文里** — *决策* 节必须以一句祈使句开头，让非技术读者也能看懂。一句话讲不清，说明还没有真正形成决策。
- **历史修正主义** — *背景* 节记录的是**当时**我们知道的事，不是现在知道的。复盘洞察留给 *影响*。

## 参见

- [[CODEMAPS/architecture|docs/CODEMAPS/architecture.md]] — 架构总览，列出当前 ADR
- [[RUNBOOK|docs/RUNBOOK.md]] §5 — 回滚流程按编号引用 ADR
- [[CONTRIBUTING|docs/CONTRIBUTING.md]] §11 — ADR PR 的评审礼仪
