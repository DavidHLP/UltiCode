---
title: Backend Modules Overview
type: overview
tags: [backend, map, modules, type/overview]
status: living
updated: 2026-06-21
sources:
  - backend-spring/src/main/java/com/ulticode/modules/
  - .claude/rules/backend/springboot-rules.md
---

# Backend Modules Overview

> [!quote] Essence
> 26 backend modules under `backend-spring/src/main/java/com/ulticode/modules/`,
> each following the [[concepts/module-layering|controller→service→mapper→entity]]
> shape. 25 have their own entity page; `vote` + `edgeoperations` are merged into
> [[entities/interactions]].

Controllers expose these `@RequestMapping` prefixes (public unless `/admin/`):

## Judging core

| Module | Prefix | Owns | Entity page |
|--------|--------|------|-------------|
| `submission` | `/submissions`, `/problems/{id}/submissions` | the Submission lifecycle, state machine, judging fence/lease, sandbox call-out | [[entities/submission]] |
| `queue` | *(internal)* | judge job queue: outbox → Redis Streams → worker; reaper for unacked entries | [[entities/judge-queue]] |
| `sandbox` | *(docker image, not a module)* | D-form harness, 4 languages, seccomp | [[entities/sandbox]] |

End-to-end: [[overview/judging-pipeline-overview]]. Pattern:
[[concepts/exactly-once-judging]].

## Core domain

| Module | Prefix | Owns | Entity page |
|--------|--------|------|-------------|
| `problem` | `/problems`, `/admin/problems`, `/problems/{id}/note` | problems, details, examples, languages, tags, versions, test cases | [[entities/problem]] |
| `contest` | `/contest`, `/admin/contest`, `/admin/scoring-rules` | contests, participants, problems, scoring rules, rankings, first-solve | [[entities/contest]] |
| `solution` | `/solutions`, `/admin/solutions` | community solutions, comments, topics | [[entities/solution]] |
| `problemlist` | `/problem-lists`, `/admin/problem-lists` | curated problem lists/collections | [[entities/problemlist]] |

## User & identity

| Module | Prefix | Owns | Entity page |
|--------|--------|------|-------------|
| `user` | `/users` | profile, settings, rating | [[entities/user]] |
| `auth` | `/auth` | login/logout, JWT issue, CSRF token | [[entities/auth]] |
| `refreshtoken` | *(via `/auth/refresh`)* | hash-only refresh token store, rotate/revoke | [[entities/refreshtoken]] |
| `permission` | *(internal)* | RBAC: `user_permissions`, `role_permissions` | [[entities/permission]] |
| `follow` | `/users` (sub-routes) | follow graph, `user_follows` | [[entities/follow]] |
| `bookmark` | `/bookmarks` | collections / collection_items | [[entities/interactions]] |
| `vote` (`edgeoperations`) | `/edge-operations` | likes/saves via `edge_operations` | [[entities/interactions]] |

Auth deep-dive: [[overview/auth-flow-overview]]. Security model:
[[concepts/security-invariants]] · [[concepts/refresh-token-hash-only-storage]] ·
[[concepts/csrf-mechanism]].

## Communication & community

| Module | Prefix | Owns | Entity page |
|--------|--------|------|-------------|
| `notification` | `/notifications`, `/admin/notifications` | notifications, preferences, delivery ledger | [[entities/notification]] |
| `websocket` | *(WS endpoint)* | real-time push (auth = access cookie only) | [[entities/websocket]] |
| `email` | `/email` | templates, logs, verification | [[entities/email]] |
| `forum` | `/forum`, `/admin/forum` | posts, comments, communities, tags | [[entities/forum]] |

Pattern: [[concepts/notification-idempotency]].

## Trust, safety & engagement

| Module | Prefix | Owns | Entity page |
|--------|--------|------|-------------|
| `moderation` | `/moderation` | reports, queue, actions (warn/ban/delete/restore), appeals | [[entities/moderation]] |
| `achievement` | `/achievements` | achievements, `user_achievements` | [[entities/achievement]] |
| `subscription` | `/subscription` | paid/VIP tier entitlements | [[entities/subscription]] |

## Platform

| Module | Prefix | Owns | Entity page |
|--------|--------|------|-------------|
| `admin` | `/admin/{users,problems,contest,forum,...}`, `/admin/dashboard`, `/admin/audit` | aggregated admin surface + `audit_logs`, `system_settings` | [[entities/admin]] |
| `search` | `/search` | search integration (MeiliSearch) | [[entities/search]] |
| `i18n` | `/i18n` | `translations` table, locale serving | [[entities/i18n]] |
| `monitoring` | `/monitoring` | health/metrics surface | [[entities/monitoring]] |
| `backup` | `/admin/backups` | DB export/restore jobs, `backups` | [[entities/backup]] |

## Cross-cutting packages (not modules)

Outside `modules/`, under `com.ulticode`:
- `common/` — `annotation`, `aspect`, `config`, `dto`, `exception`, `filter`,
  `metrics`, `response` (`Result<T>`), `service`, `util`.
- `infrastructure/redis` — Redisson client.
- `security/` — JWT filter chain, CSRF filter, cookie handlers, `@CheckBan` /
  `@RateLimit` / `@Audited` (role enforcement uses Spring `@PreAuthorize`; identity via `SecurityUtil`).

## How to pick where to look

- "How does feature X work?" → its entity page above, or this table.
- "What endpoints exist?" → grep `@RequestMapping` in the module's `controller/`.
- "What tables back this?" → the entity page's **Key tables** section, or
  [[overview/database-schema-overview]].
- "What's the convention for adding a module?" → `AGENTS.md` + `.claude/rules/backend/`.

## Links out

> [!link] Related pages
> - [[overview/architecture-overview]] · [[overview/auth-flow-overview]]
> - [[overview/judging-pipeline-overview]] · [[overview/database-schema-overview]]
> - [[concepts/module-layering]]