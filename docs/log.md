---
title: 维护日志（log）
tags: [index, docs, workflow]
status: living
updated: 2026-06-21
owner: architect
---

# 维护日志（log）

> append-only 时间线，记录 wiki 的 ingest / maintain 事件。格式见 [[SCHEMA#3 indexmd 与 logmd 格式]]。
> 用 `grep "^## \[" log.md | tail -5` 看最近条目。

## [2026-06-21] maintain | docs 重构为 llm-wiki 三层结构

把 `docs/` 从「手写工程文档 + ADR + CODEMAPS 镜像」完全重构为 karpathy *llm-wiki* 格式（实例化为本项目形态）。

**治理层瘦身**（合并冗余、统一格式）：

- 合并 `WIKI.md` + `DOCS_CONVENTIONS.md` → 单一 [[SCHEMA]]（三层 / 三动作 / 命名 / frontmatter / 链接 / 归档 / index+log 格式）
- 新建 [[index]]（唯一全局 catalog，吸收原 README §1–§4 + 角色入口表）
- `README.md` 瘦身为极简着陆（入口指针）
- 新建本文件（log）

**落地 llm-wiki 核心产物**（entity / concept 综合页，首批 5 个，信息源密度最高）：

- `entities/`：[[contest]]、[[submission]]、[[sandbox-dform]]
- `concepts/`：[[exactly-once]]、[[virtual-contest]]

**保留不动**：`adr/`（12 篇）、`CODEMAPS/`（不重命名——doc-updater agent 硬编码此路径）、`RUNBOOK.md`、`ENV.md`、`CONTRIBUTING.md`、`ops/`、`theme/`、`screenshots/`。

**暂缓**（信息源单薄，等专属 ADR / 实现文档）：csrf-token、refresh-token、csrf-lifecycle。

**下游同步**：根 `CLAUDE.md` 文档目录表、子目录 README 的 wikilink 引用（`[[WIKI]]`/`[[DOCS_CONVENTIONS]]` → `[[SCHEMA]]`）。
