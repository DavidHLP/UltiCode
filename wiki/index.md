---
title: Index
type: index
tags: [meta, type/index]
status: living
updated: 2026-07-07
sources: []
---

# Index

> [!info] How to maintain
> Catalog of every page in the wiki, grouped by type. One line per page. When you
> ingest a new source, add its line here and append to [`log.md`](log.md). See
> [`SCHEMA.md`](SCHEMA.md) for the conventions; graph-view coloring keys off the
> `type/<x>` tag and is set up via [`concepts/obsidian-graph-coloring`](concepts/obsidian-graph-coloring.md).

## Meta

- [README](README.md) — landing: what the wiki is, 3 layers, 3 operations, how to use
- [SCHEMA](SCHEMA.md) — the convention doc: layers, operations, frontmatter, linking, writing rules
- [Index](index.md) — this catalog
- [Log](log.md) — append-only maintenance timeline
- Page manifest — `wiki/.meta/manifest.json`, per-page git provenance (last commit + body hash); see [SCHEMA §12](SCHEMA.md)

## Overviews — whole-system synthesis

- [Architecture Overview](overview/architecture-overview.md) — system map, request lifecycle, tech stack
- [Backend Modules Overview](overview/backend-modules-overview.md) — all 26 modules, prefixes, ownership
- [Frontend Apps Overview](overview/frontend-apps-overview.md) — console + management + shared, API styles
- [Judging Pipeline Overview](overview/judging-pipeline-overview.md) — submit → queue → sandbox → judge → notify
- [Auth Flow Overview](overview/auth-flow-overview.md) — login → JWT → CSRF → WebSocket → RBAC
- [Database Schema Overview](overview/database-schema-overview.md) — tables by domain + migration timeline
- [Dev Environment Overview](overview/dev-environment-overview.md) — PM2 + docker + scripts + Arthas + traps

## Entities — domain objects & modules

- [Submission](entities/submission.md) — judging state machine, generation fence, lease
- [Contest](entities/contest.md) — lifecycle, scoring rules, rankings, virtual mode
- [Problem](entities/problem.md) — CRUD, test cases, versions, notes
- [Notification](entities/notification.md) — intent + delivery ledger + push
- [User](entities/user.md) — profile, settings, rating
- [Auth](entities/auth.md) — login/logout, token issue, CSRF mint
- [Forum](entities/forum.md) — posts, comments, communities, tags
- [Moderation](entities/moderation.md) — reports, queue, action handlers, appeals
- [Judge Queue](entities/judge-queue.md) — outbox → streams → worker (hexagonal)
- [Sandbox (D-form)](entities/sandbox.md) — judge image, 4 languages, seccomp
- [Solution](entities/solution.md) — community writeups + comments + topics
- [Problem List](entities/problemlist.md) — curated problem collections
- [WebSocket](entities/websocket.md) — realtime push, cookie-only auth
- [Permission](entities/permission.md) — RBAC roles + per-user grants
- [Refresh Token](entities/refreshtoken.md) — hash-only refresh store
- [Email](entities/email.md) — templates, logs, verification
- [Admin](entities/admin.md) — aggregated admin surface, audit logs, system settings
- [Search](entities/search.md) — MeiliSearch full-text across problems/users/posts/solutions
- [I18n](entities/i18n.md) — per-entity translation rows, locale serving
- [Monitoring](entities/monitoring.md) — admin-only system/DB/queue/Redis/health telemetry
- [Backup](entities/backup.md) — DB export/restore with scheduled runs
- [Achievement](entities/achievement.md) — badge catalog + event-driven earnings
- [Follow](entities/follow.md) — directed follow graph on `/users/{id}/follow*`
- [Subscription](entities/subscription.md) — paid tier / VIP entitlements
- [Interactions](entities/interactions.md) — bookmarks, votes, edge operations merged

## Concepts — decisions, patterns, invariants

- [Achievement Projection](concepts/achievement-projection.md) — ADR-0005: read-model projection for achievement queries
- [Admin Comment-Read Port](concepts/admin-comment-read-port.md) — ADR-0008: AdminReadModel forum phase, typed-view cross-module reads
- [Admin Projection Inversion](concepts/admin-projection-inversion.md) — ADR-0011: phased `AdminXxxProjection` rollout (Stage 1 ProblemList, Stage 2 Submission/User, Stage 3 Analytics)
- [Admin User-Stats Read Port](concepts/admin-user-stats-read-port.md) — ADR-0007: AdminReadModel user phase, per-user stats via primitives
- [Contest Live-Ranking Read Port](concepts/contest-live-ranking-read-port.md) — ADR-0010: `RankingService.getLiveRanking` → narrow port, real seam
- [Moderation Projection](concepts/moderation-projection.md) — ADR-0004: 10 pure-read methods + 3 projections extracted from 760-LOC service
- [Problem Detail Port](concepts/problem-detail-port.md) — ADR-0006: 137-LOC write-side satellite orchestration extracted from `ProblemServiceImpl.updateProblem`
- [Realtime Push Port Series](concepts/realtime-push-port-series.md) — ADR-0009: `RealtimeService` god service collapsed into 6 per-consumer ports
- [Submission-Contest Port](concepts/submission-contest-port.md) — ADR-0001: dependency inversion, 4 contest mappers → 1 port
- [Exactly-Once Judging](concepts/exactly-once-judging.md) — outbox + generation fence + lease
- [Notification Idempotency](concepts/notification-idempotency.md) — intent + delivery ledger dedup
- [Notification Dispatch & Preferences](concepts/notification-dispatch-and-preferences.md) — ADR-004: typed dispatcher, preference gate, 4 categories, migration flag
- [Refresh Token Hash-Only Storage](concepts/refresh-token-hash-only-storage.md) — irrecoverable refresh tokens
- [Virtual Contest](concepts/virtual-contest.md) — isolated replay sessions
- [Security Invariants](concepts/security-invariants.md) — the non-negotiable rules
- [CSRF Mechanism](concepts/csrf-mechanism.md) — Redis double-submit token
- [Sandbox Security Contract](concepts/sandbox-security-contract.md) — seccomp + zero-import preamble
- [Sandbox Rebuild Runbook](concepts/sandbox-rebuild.md) — masked-RE diagnostic + alpine/musl/proxy rebuild flow
- [Theme System](concepts/theme-system.md) — 4-layer, LXGW WenKai, FOUC killer
- [Result Envelope & Case Mapping](concepts/result-envelope-and-case-mapping.md) — Result&lt;T&gt; + snake↔camel
- [Module Layering](concepts/module-layering.md) — controller→service→mapper→entity
- [Flyway Migration Discipline](concepts/flyway-migration-discipline.md) — append-only, no credential seeds
- [Arthas Diagnostics](concepts/arthas-diagnostics.md) — STATELESS MCP + degrade path
- [Process Management](concepts/process-management.md) — PM2 vs Preview modes, port mutex, doctor.sh
- [Sidebar Menu Visual Contract](concepts/sidebar-menu.md) — ADR-005: `.uc-sidebar-*` shared CSS contract, two-tier naming, icon-neutral
- [Obsidian Graph Coloring](concepts/obsidian-graph-coloring.md) — `type/<x>` tag → graph colors, one-time per-machine UI setup

---

**Counts**: 4 meta · 8 overviews · 26 entities · 27 concepts = **65 content pages**
(authoritative per-type counts: `wiki/.meta/manifest.json` → `stats.by_type`, SCHEMA §12).
