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

## [2026-06-21] bootstrap | Initial wiki scaffold (39 pages)

Created the wiki from a clean `docs/` directory, following the LLM Wiki pattern
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
`docs/theme|ops|adr|CODEMAPS` paths and need updating separately; the wiki is the
knowledge layer and does not duplicate the command layer.

