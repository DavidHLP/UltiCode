---
title: UltiCode Wiki
type: index
tags: [meta, type/index, type/landing]
status: living
updated: 2026-07-05
sources:
  - AGENTS.md
  - CLAUDE.md
---

# UltiCode Wiki

> [!tldr] One-sentence summary
> A persistent, **LLM-maintained** knowledge base for the UltiCode online-judge
> platform — compiled once and kept current, not re-derived on every question.

You read it. The LLM writes and maintains all of it — the codebase's own
companion wiki: cross-references already wired, architecture already mapped,
hard decisions already recorded.

> Browse in Obsidian for the graph view, or read on GitHub — every link resolves
> either way.

## Start here

| If you want to… | Read |
|-----------------|------|
| See the whole system | [`overview/architecture-overview.md`](overview/architecture-overview.md) |
| Map all backend modules | [`overview/backend-modules-overview.md`](overview/backend-modules-overview.md) |
| Trace how a submission gets judged | [`overview/judging-pipeline-overview.md`](overview/judging-pipeline-overview.md) |
| Understand login & sessions | [`overview/auth-flow-overview.md`](overview/auth-flow-overview.md) |
| Get the app running locally | [`overview/dev-environment-overview.md`](overview/dev-environment-overview.md) |
| Browse everything | [`index.md`](index.md) |

## How the wiki works

Three layers (raw sources → this wiki → the schema) and three operations
(ingest · query · lint). The wiki is the **knowledge layer** (how things work
and why); `AGENTS.md` / `CLAUDE.md` are the **command layer** (what to do) —
the wiki links to them but never copies their directives.

> Full conventions, frontmatter, linking rules, and workflows live in
> [`SCHEMA.md`](SCHEMA.md). This landing page is navigation only.

## Layout

```
wiki/
├── README.md   SCHEMA.md   index.md   log.md
├── overview/   # synthesis — architecture, pipelines, whole-system maps
├── entities/   # domain objects & backend modules
├── concepts/   # cross-cutting decisions, patterns, invariants
├── templates/  # Obsidian templates (entity / concept / overview / daily-note)
└── daily-notes/  # one file per day — human ingest journal (SCHEMA §10)
```

## Status

Bootstrapped 2026-06-21; evolution timeline in [`log.md`](log.md), per-page git
provenance in [`wiki/.meta/manifest.json`](.meta/manifest.json) (SCHEMA §12).
Pages marked `status: stub` in frontmatter are placeholders awaiting a deeper
ingest pass.
