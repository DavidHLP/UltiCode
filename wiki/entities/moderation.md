---
title: Moderation
type: entity
tags: [moderation, trust-safety, core, type/entity]
status: living
updated: 2026-06-21
sources:
  - backend-spring/src/main/java/com/ulticode/modules/moderation/
  - init-db/migrations/V20260619120000__Seed_Moderation_Data.sql
aliases: [审核, 内容审核]
---

# Moderation

Content trust & safety: reports → queue → action → appeal. Actions are dispatched
through a **strategy handler chain** (one handler per action type), so adding a new
action type is a new handler, not a fork in the service.

## Responsibility

Owns the report intake, moderation queue, action execution (warn/ban/delete/restore
/ dismiss), and the appeal workflow.

## Key tables

| Table | Purpose |
|-------|---------|
| `reports` | user/system-filed reports |
| `moderation_queue` | triage queue |
| `moderation_actions` | executed actions (history) |
| `user_warnings` | active warnings |
| `user_bans` | active bans |
| `appeals` | user appeals against actions |

## Enums

`ReportStatus` · `ReportCategory` · `ModerationStatus` · `ModerationActionType` ·
`AppealStatus`.

## Action handler chain (strategy)

`ModerationActionHandler` is the base; each action type has a dedicated handler in
`service/impl/`:

| Handler | Action |
|---------|--------|
| `WarnHandler` | issue `user_warnings` |
| `BanHandler` | issue `user_bans` (drives `@CheckBan`) |
| `DeleteHideHandler` | remove/hide reported content |
| `RestoreDismissHandler` | undo / dismiss |
| `AppealHandler` | process `appeals` |

`ModerationServiceImpl` dispatches by `ModerationActionType` (note: DTO field
`PerformModerationActionDTO.action` is raw `String` — see
[[concepts/result-envelope-and-case-mapping]] on the enum gap).

## Controllers

- `ModerationController` → `/moderation` (queue, reports, actions, appeals, stats).

## Source files

- `backend-spring/.../modules/moderation/` (controller, service + impl handlers, entity, dto, mapper).
- `init-db/migrations/V20260619120000__Seed_Moderation_Data.sql`.

## Cross-links

- [[entities/user]] · [[entities/forum]] (UGC source)
- [[concepts/security-invariants]] · [[concepts/result-envelope-and-case-mapping]]
- [[overview/backend-modules-overview]]

## Gotchas

- Audit identity for an action = authenticated principal, not the DTO body.
- A ban writes `user_bans`; `@CheckBan` reads it on subsequent requests — keep the
  read path cheap (indexed).
- Appeals can reverse an action; `moderation_actions` is append-only history, the
  *current* state is derived.
