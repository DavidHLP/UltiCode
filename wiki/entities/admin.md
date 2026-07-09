---
title: Admin
type: entity
tags: [admin, platform, core, type/entity]
status: living
updated: 2026-06-21
sources:
  - backend-spring/src/main/java/com/ulticode/modules/admin/
  - backend-spring/src/main/java/com/ulticode/modules/admin/controller/AdminSettingsController.java
  - backend-spring/src/main/java/com/ulticode/modules/admin/controller/AuditController.java
  - backend-spring/src/main/java/com/ulticode/modules/admin/controller/DashboardController.java
  - backend-spring/src/main/java/com/ulticode/modules/admin/entity/AuditLog.java
  - backend-spring/src/main/java/com/ulticode/modules/admin/entity/SystemSetting.java
aliases: [管理后台]
---

# Admin

Aggregated administrative surface — ~145 files spread across 15+ sub-controllers.
Rather than an actual product module, `admin` is the **back-office kitchen sink**:
each controller proxies a slice of another module (problems, contests, forum…)
and adds two cross-cutting tables (`audit_logs`, `system_settings`).

## Responsibility

Everything under `/admin/**` except the few `/admin/...` prefixes owned by
specialised modules (`/admin/solutions` → [[entities/solution]],
`/admin/contest` → [[entities/contest]], `/admin/scoring-rules` → scoring, etc.).
What remains is admin-only UX: dashboards, system settings, audit search, and
admin-only CRUD on the bookkeeping tables.

## Key tables

| Table | Purpose |
|-------|---------|
| `audit_logs` | append-only record of admin actions (`AuditLog` entity, `oldValues`/`newValues` as JSON) |
| `system_settings` | per-category JSON rows (one row per settings category: `general`, `email`, `rate-limits`, `uploads`, `features`) |

Other tables referenced from admin controllers live in their owning module —
e.g. `users` in [[entities/user]], `problems` in [[entities/problem]].

## Controllers

Fifteen sub-controllers under `admin/controller/`. The big ones:

| Controller | Prefix | Notes |
|------------|--------|-------|
| `AdminUserController` | `/admin/users` | user CRUD, ban, role |
| `AdminProblemController` | `/admin/problems` | problem CRUD (largest) |
| `AdminContestController` | `/admin/contest` | contest CRUD (owned by contest module) |
| `AdminSolutionController` | `/admin/solutions` | solution moderation |
| `AdminForumController` | `/admin/forum` | forum moderation |
| `AdminCommentController` | `/admin/comments` | comment moderation |
| `AdminProblemListController` | `/admin/problem-lists` | problem-list curation |
| `AdminSubmissionController` | `/admin/submissions` | rejudge, view |
| `AdminTestCaseController` | `/admin/test-cases` | problem test-case mgmt |
| `AdminTagController` | `/admin/tags` | tag taxonomy |
| `AdminNotificationController` | `/admin/notifications` | broadcast |
| `AdminAnalyticsController` | `/admin/analytics` | per-domain analytics services |
| `AdminAccountController` | `/admin/account` | self-service admin profile |
| `AdminSettingsController` | `/admin/settings` | `system_settings` reader/writer, maintenance mode, cache-clear |
| `AuditController` | `/admin/audit` | list/stats/CSV+JSON export of `audit_logs` |
| `DashboardController` | `/admin/dashboard` | dashboard stats + chart series (whitelisted metric+period) |

## System settings persistence

`SystemSetting` is a key-value row keyed by a category string. Each
`AdminSettingsController` endpoint reads/writes one category as a JSON-serialised
VO. The PK column is `key` — a MySQL reserved word, hence the backticks
(`@TableId("`key`")`). Maintenance mode and feature toggles are first-class
categories; see `AdminSettingsController.toggleMaintenance()`.

## Audit identity invariant

`AuditLog.performerId` is the **authenticated principal** captured at the
controller boundary, never a field from the request body. Sub-controllers that
mutate state (ban, role change, problem delete…) wire an `@Audited` aspect that
records `oldValues`/`newValues` as JSON via `JacksonTypeHandler`. The identity
invariant is non-negotiable — see `AGENTS.md` § Security Invariants.

`AuditController.export` streams CSV/JSON with the same `AuditLogQueryDTO`
filter; the format switch is fail-closed (returns 400 + `Result.error` for
unknown formats).

## Source files

- `backend-spring/.../modules/admin/` (controller, service + impl, entity, dto, mapper).
- The `bootstrap/` package contains `AdminBootstrapRunner` (opt-in dev profile
  only; see `AGENTS.md`).

## Cross-links

- [[entities/user]] · [[entities/problem]] · [[entities/contest]] · [[entities/solution]] · [[entities/forum]] · [[entities/moderation]] · [[entities/monitoring]] · [[entities/backup]]
- [[overview/backend-modules-overview]]

## Gotchas

- `AuditLog.oldValues` / `newValues` are `Map<String, Object>` — never
  deserialize without a DTO contract; the column is JSON, not a fixed schema.
- `system_settings.key` is a MySQL reserved word; the `@TableId("`key`")` is
  load-bearing — do not strip the backticks.
- Many admin endpoints double-encode `@PreAuthorize` (route + class) — keep
  both, never remove the class-level annotation when adding a new method.
- Dashboard chart series uses a regex allow-list (`users|submissions|...`) —
  free-form `metric` strings return 400; keep the regex in sync with
  `ALLOWED_METRICS`.
