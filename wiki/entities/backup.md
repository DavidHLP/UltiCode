---
title: Backup
type: entity
tags: [backup, platform, ops, type/entity]
status: living
updated: 2026-06-21
sources:
  - backend-spring/src/main/java/com/ulticode/modules/backup/
  - backend-spring/src/main/java/com/ulticode/modules/backup/controller/BackupController.java
  - backend-spring/src/main/java/com/ulticode/modules/backup/entity/Backup.java
  - backend-spring/src/main/java/com/ulticode/modules/backup/scheduler/BackupScheduler.java
aliases: [备份恢复]
---

# Backup

Database export and restore, scheduled and on-demand. Each backup is a
file-on-disk tracked by a `backups` row that captures the lifecycle (status,
size, error), with free-form JSON metadata.

## Responsibility

Owned entirely by admin users. The module produces a downloadable artifact
(SQL dump or compressed archive), tracks it through the
`PENDING → RUNNING → COMPLETED / FAILED` state machine, and restores from a
previously completed artifact.

## Key tables

| Table | Purpose |
|-------|---------|
| `backups` | one row per backup run; `metadata` is `Map<String, Object>` (JSON) via `JacksonTypeHandler`; `@TableName(autoResultMap = true)` is load-bearing for the type handler |

`BackupStatus` (`PENDING` / `RUNNING` / `COMPLETED` / `FAILED`) and
`BackupType` (e.g. `FULL`, `INCREMENTAL`) are the enums.

## Controllers

`BackupController` → `/admin/backups`:

| Endpoint | Notes |
|----------|-------|
| `POST /admin/backups` | create on demand; `@RateLimit(30/60s)`; records `createdBy` from `SecurityUtil.getCurrentUserId()` |
| `GET /admin/backups` | list with `BackupQueryDTO` filter, paginated |
| `GET /admin/backups/{id}` | detail |
| `GET /admin/backups/{id}/download` | stream the file as `application/octet-stream` with `filename*=UTF-8''...` |
| `POST /admin/backups/{id}/restore` | restore; `@RateLimit(30/60s)`; `userId` recorded |
| `DELETE /admin/backups/{id}` | delete row + file |

## Scheduler

`BackupScheduler` runs the periodic backup job (cron-driven). It marks each run
`PENDING → RUNNING → COMPLETED/FAILED`; on `FAILED` it writes the error string
to the `error` column.

## autoResultMap

`Backup` is annotated `@TableName(value = "backups", autoResultMap = true)`.
This is required so MyBatis-Plus generates a `<resultMap>` for the `metadata`
column's `JacksonTypeHandler` — without `autoResultMap = true`, the JSON column
will not be deserialised and the field will be `null` at read time.

## Source files

- `backend-spring/.../modules/backup/` (controller, service + impl, entity, dto, mapper, scheduler).

## Cross-links

- [[entities/admin]] (admin surface host)
- [[overview/backend-modules-overview]] · [[overview/dev-environment-overview]]

## Gotchas

- `metadata` is JSON, not a fixed schema — the service is the only place that
  should know the keys. Don't bind `metadata` to a typed DTO at the controller.
- `createBackup` records `userId` from `SecurityUtil.getCurrentUserId()` (falls
  back to `"anonymous"` if null); same for `restoreBackup`. This is the audit
  anchor — see `AGENTS.md` § Security Invariants.
- `restore` is destructive and rate-limited (30/min); there is no dry-run. Test
  restore on a non-prod DB first.
- The download response uses RFC 5987 (`filename*=UTF-8''...`) — never plain
  `filename=`, which breaks for non-ASCII names.
