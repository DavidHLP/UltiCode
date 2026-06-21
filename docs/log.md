---
title: 维护时间线
tags: [index]
status: living
updated: 2026-06-21
owner: architect
---

# 维护时间线（log）

> Append-only。条目格式 `## [YYYY-MM-DD] ingest|maintain|bootstrap | Title`。
> 最近 5 条：`grep "^## \[" docs/log.md | tail -5`。规范见 [[SCHEMA#3-indexmd-与-logmd-格式]]。

## [2026-06-21] bootstrap | 从零构建 LLM Wiki 基线

按 karpathy *LLM Wiki* 模式从零建立 `docs/` 知识库（不恢复此前被删的旧实现）。基于当日代码真实状态（经 codegraph + 批量源码核对）创建 **25 个文件**：

**骨架（4）**：[[README]]、[[SCHEMA]]、[[index]]、本文件。

**实体页（5）**：[[submission]]、[[contest]]、[[sandbox-d-form]]、[[judge-queue]]、[[refresh-token]]。

**概念页（4）**：[[exactly-once-judging]]、[[virtual-contest]]、[[shadow-mode-cutover]]、[[notification-idempotency]]。

**决策记录（5）**：[[decisions/README]]、[[0001-judge-outbox-and-generation-fencing]]、[[0002-sandbox-d-form-hexagonal]]、[[0003-refresh-token-hash-only-storage]]、[[0004-notification-intent-and-delivery-ledger]]。

**架构镜像（4）**：[[codemap/README]]、[[codemap/backend-modules]]、[[codemap/judging-pipeline]]、[[codemap/frontend-apps]]。

**运维深读（2）**：[[ops/README]]、[[ops/arthas-runtime-diagnostics]]。

**主题专题（1）**：[[theme/README]]。

**命名选择**：目录用 `decisions/`（非 `adr/`）、小写 `codemap/`（非大写 `CODEMAPS/`），与其余目录统一；`raw/` 暂不创建（首次真实外部源 ingest 时再建）。

**验证**：`ls -R docs/` 核对 25 文件；[[index]] 的 wikilink 全部可解析；每个 entity/concept/decision 的 `sources:` 指向真实代码路径或迁移全文件名。

**Follow-up（待定）**：项目根 `CLAUDE.md` 有一段引用旧 `CODEMAPS/` / `adr/` 的 `docs/` 描述，与本设计有出入，是否同步更新待用户确认。
