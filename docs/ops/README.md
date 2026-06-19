# `docs/ops/` — Operations & Tooling Reference

> **Purpose**: this directory holds **operational runbooks and tooling references** —
> not project-level policies (those live at `docs/` top level: `ENV.md`,
> `CONTRIBUTING.md`, `RUNBOOK.md`) and not architecture codemaps (those live in
> `docs/CODEMAPS/`).

## When to put a doc here

Use `docs/ops/` when the doc is:

- A **deep-dive reference for a specific operational tool** (e.g. Arthas MCP,
  Prometheus scrapers, log query recipes)
- A **playbook for a specific operational scenario** that doesn't fit cleanly
  into `RUNBOOK.md`'s incident-response flow (e.g. disaster recovery, certificate
  rotation, dependency upgrade)
- A **tooling config reference** that needs a stable, linkable URL (e.g. how a
  specific Maven plugin or Docker layer is configured)

Do **not** use `docs/ops/` for:

- **Project-wide policy** → `docs/CONTRIBUTING.md` (dev setup, style, PR process)
- **Incident response playbooks** of the "X is down, do Y" variety → `docs/RUNBOOK.md` §4
- **Architecture / code structure** → `docs/CODEMAPS/`
- **Architecture decisions** (rationale, alternatives, status) → `docs/adr/`
- **Time-boxed task plans / reviews** → `docs/<feature>/`

## Naming

- `kebab-case-lowercase.md` (matches the file's H1 title, no UPPER_CASE unless
  the concept itself is an acronym — e.g. `sandbox-d-form-flow.md` is fine,
  `k8s-mtls-rotation.md` is fine)
- No `.txt` or numbered prefixes (`01-`, `02-`) — order is alphabetical, not
  prescribed
- File name should be **concept-first** (e.g. `arthas-mcp-usage.md`, not
  `usage-of-arthas-mcp.md`)

## Frontmatter Convention

Every doc here should carry YAML frontmatter (top of file) so a future
`/ecc:update-docs` pass can index them:

```yaml
---
title: <one-line, in H1 style>
tags: [ops, <subsystem>, <status>]
status: living | frozen | draft
updated: YYYY-MM-DD
owner: backend | frontend | devops | <module>
---
```

- `tags` — searchable vocabulary (`ops`, `diagnostics`, `arthas`, `reference`)
- `status: living` — actively maintained, may be re-verified each session
- `status: frozen` — historical reference, do not edit without good reason
- `status: draft` — incomplete, do not link from production docs
- `owner` — module/team that owns the doc (matches CODEOWNERS if present)

## Index

| File                                                | Status | Owner    | Purpose                                                |
| --------------------------------------------------- | ------ | -------- | ------------------------------------------------------ |
| [`arthas-mcp-usage.md`](./arthas-mcp-usage.md)     | living | backend  | Arthas MCP watch/trace/stack 实战 (CLAUDE.md §运行时调试 配套) |

> 文档新增/移除/重命名时:同步更新本表 + 在 `docs/COODEMAPS/architecture.md` 数据流段落
> 提及的 "运行时诊断" 链接(若有)。
