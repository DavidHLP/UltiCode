---
title: UltiCode Wiki
type: index
tags: [meta]
status: living
updated: 2026-06-21
sources:
  - AGENTS.md
  - CLAUDE.md
---

# UltiCode Wiki

A persistent, **LLM-maintained** knowledge base for the UltiCode online-judge
platform. Unlike RAG — which re-derives answers from raw files on every question —
this wiki is **compiled once and kept current**. Each page is a distilled,
interlinked summary of what the LLM has read in the codebase, and it gets richer
with every source ingested and every question answered.

You read it. The LLM writes and maintains all of it. Think of it as the codebase's
own companion wiki — the cross-references are already wired, the architecture is
already mapped, the hard decisions are already recorded.

> Browse in Obsidian for the graph view, or read on GitHub — every link resolves either way.

## Start here

| If you want to… | Read |
|-----------------|------|
| See the whole system | [`overview/architecture-overview.md`](overview/architecture-overview.md) |
| Map all 26 backend modules | [`overview/backend-modules-overview.md`](overview/backend-modules-overview.md) |
| Trace how a submission gets judged | [`overview/judging-pipeline-overview.md`](overview/judging-pipeline-overview.md) |
| Understand login & sessions | [`overview/auth-flow-overview.md`](overview/auth-flow-overview.md) |
| Get the app running locally | [`overview/dev-environment-overview.md`](overview/dev-environment-overview.md) |
| Browse everything | [`index.md`](index.md) |

## The three layers

| Layer | What | Owner |
|-------|------|-------|
| **Raw sources** | The codebase + `AGENTS.md` / `CLAUDE.md` / `.claude/rules/`. Immutable truth. | Humans |
| **The wiki** | This `docs/` tree — distilled, interlinked markdown. | LLM |
| **The schema** | [`SCHEMA.md`](SCHEMA.md) — how the wiki is structured & maintained. | Human + LLM |

The wiki is the **knowledge layer** (how things work and why). `AGENTS.md` and
`CLAUDE.md` are the **command layer** (what to do). The wiki explains and links to
them but never copies their directives.

## The three operations

- **Ingest** — drop a source, the LLM reads it and folds it into entities/concepts/overviews, then updates `index.md` and `log.md`.
- **Query** — ask a question; the LLM reads the relevant pages and answers with citations. Good answers get filed back as new pages.
- **Lint** — periodic health check: orphans, dead links, stale claims, missing cross-refs.

Full workflows in [`SCHEMA.md`](SCHEMA.md) § 2.

## Layout

```
docs/
├── README.md   SCHEMA.md   index.md   log.md
├── overview/   # synthesis — architecture, pipelines, whole-system maps
├── entities/   # domain objects & backend modules
└── concepts/   # cross-cutting decisions, patterns, invariants
```

## Status

Bootstrapped **2026-06-21** with 39 pages (4 skeleton + 7 overviews + 16 entities
+ 12 concepts). See [`log.md`](log.md) for the evolution timeline. Pages marked
`status: stub` in frontmatter are placeholders awaiting a deeper ingest pass.
