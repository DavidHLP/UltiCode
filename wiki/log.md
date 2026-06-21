---
title: Maintenance Log
type: log
tags: [meta]
status: living
updated: 2026-06-21
sources: []
---

# Maintenance Log

Append-only timeline of wiki evolution. Greppable: `grep "^## \[" log.md | tail -5`.

Entry types: `bootstrap` · `ingest` · `query-filed` · `lint` · `fix`.

Format: `## [YYYY-MM-DD] <type> | <summary>`

---

## [2026-06-21] ingest | Promoted 9 low-density modules to entity pages (admin/search/i18n/monitoring/backup/achievement/follow/subscription + interactions merge)

Closed the 10 follow-up module page gaps from the initial bootstrap. Eight
modules got their own page; `bookmark` + `vote` + `edgeoperations` were
**merged** into a single `interactions` page because the three modules share
the same widget surface (the "interactions" UI hits all three) and the data
model shares `targetType` semantics. The `backend-modules-overview` table was
rewritten to point at the new pages (the previous `(table only)` markers
across `admin` / `search` / `i18n` / `monitoring` / `backup` /
`achievement` / `follow` / `bookmark` / `vote` / `subscription` rows are
gone). `index.md` updated to 25 entities; total now **48 pages**.

**New entity pages (8)**: `admin`, `search`, `i18n`, `monitoring`, `backup`,
`achievement`, `follow`, `subscription`.

**New merged entity page (1)**: `interactions` (`bookmark` + `vote` +
`edgeoperations`).

**Updated**: `overview/backend-modules-overview.md` (10 row links + the
"16/10" intro), `index.md` (10 new lines + counts), `log.md` (this entry).

**Design notes**:
- `search` is **MeiliSearch**, not Elasticsearch — corrected from the
  original `(Elasticsearch-shaped)` table marker.
- `interactions` deliberately keeps three Java modules' detail in one
  page; splitting them would repeat the same widget flow three times.
- `subscription` has **two** controllers (`/admin/subscriptions` +
  `/subscription`); only the user-facing one is non-admin.

## [2026-06-21] bootstrap | Initial wiki scaffold (39 pages)

Created the wiki from a clean `wiki/` directory, following the LLM Wiki pattern
and [`SCHEMA.md`](SCHEMA.md). Decisions locked: full first cut (~39 pages),
pure-idea taxonomy (`overview/` + `entities/` + `concepts/`), English prose.

**Skeleton (4)**
- `README.md`, `SCHEMA.md`, `index.md`, `log.md`

**`overview/` (7)** — architecture, backend-modules (26-row map), frontend-apps,
judging-pipeline, auth-flow, database-schema, dev-environment.

**`entities/` (16)** — submission, contest, problem, notification, user, auth,
forum, moderation, judge-queue, sandbox, solution, problemlist, websocket,
permission, refreshtoken, email.

**`concepts/` (12)** — exactly-once-judging, notification-idempotency,
refresh-token-hash-only-storage, virtual-contest, security-invariants,
csrf-mechanism, sandbox-security-contract, theme-system,
result-envelope-and-case-mapping, module-layering, flyway-migration-discipline,
arthas-diagnostics.

**Distilled from**: `AGENTS.md`, `CLAUDE.md`, `.claude/rules/`,
`backend-spring/.../modules/` (26 modules), `init-db/migrations/` (35 migrations),
`docker/sandbox/`, `scripts/dev/`, `ecosystem.config.cjs`, `shared/`.

**Deliberately not done this pass** (follow-up): low-density modules (admin,
bookmark, vote, edgeoperations, i18n, monitoring, backup, search, achievement,
follow, subscription) are covered by the `backend-modules-overview` table rather
than their own entity page; `CLAUDE.md`/`AGENTS.md` still reference the old
`wiki/theme|ops|adr|CODEMAPS` paths and need updating separately; the wiki is the
knowledge layer and does not duplicate the command layer.

