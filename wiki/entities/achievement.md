---
title: Achievement
type: entity
tags: [achievement, gamification, type/entity]
status: living
updated: 2026-06-21
sources:
  - backend-spring/src/main/java/com/ulticode/modules/achievement/
  - backend-spring/src/main/java/com/ulticode/modules/achievement/controller/AchievementController.java
  - backend-spring/src/main/java/com/ulticode/modules/achievement/entity/Achievement.java
  - backend-spring/src/main/java/com/ulticode/modules/achievement/entity/UserAchievement.java
aliases: [成就]
---

# Achievement

Gamification layer — admins define `achievements` (badges with criteria +
points), and users earn them. Earnings live in `user_achievements`; unlocking is
event-driven via the `event/` + `listener/` packages reacting to
[[entities/submission|submission]] and [[entities/contest|contest]] domain
events.

## Responsibility

Define the badge catalog and track per-user progress/earnings. Read endpoints
serve both the user ("my achievements") and admin views. Write endpoints are
admin-only.

## Key tables

| Table | Purpose |
|-------|---------|
| `achievements` | the catalog row; `criteria` is a JSON column (Map<String,Object>) via `JacksonTypeHandler`; `key` is a MySQL reserved word and is backticked in `@TableField("`key`")` |
| `user_achievements` | earned-badge rows; one per (user, achievement) pair |

`Achievement` carries `tier` (1=bronze … 4=platinum) and `points` (awarded on
earn). `UserAchievement` is the immutable earn log; earning the same
achievement twice is a no-op.

## Controllers

`AchievementController` → `/achievements`:

| Endpoint | Notes |
|----------|-------|
| `GET /achievements` | paginated catalog; `AchievementQueryDTO` |
| `GET /achievements/{id}` | detail |
| `GET /achievements/user/me` | current user's progress |
| `GET /achievements/user/me/points` | current user's total points |
| `GET /achievements/my` | alias for `/user/me` (frontend convenience) |
| `GET /achievements/points` | alias for `/user/me/points` |
| `GET /achievements/user/{id}` | any user's progress |
| `POST /achievements` | admin create; `@RateLimit(30/60s)` |
| `PUT /achievements/{id}` | admin update |
| `DELETE /achievements/{id}` | admin delete |

## Flow

domain event (e.g. submission judged) → `listener/` → evaluate matching
`criteria` from `achievements` → insert `user_achievements` row → on first earn
of the badge, award `points` to the user.

## Source files

- `backend-spring/.../modules/achievement/` (controller, service + impl,
  entity, dto, mapper, event, listener, constants).

## Cross-links

- [[entities/submission]] · [[entities/contest]] · [[entities/user]]
- [[overview/backend-modules-overview]]

## Gotchas

- `Achievement.key` is a MySQL reserved word; the backticks in
  `@TableField("`key`")` are load-bearing — the column backticks in generated
  SQL depend on them.
- `criteria` is a JSON column (`autoResultMap = true` on `Achievement`); the
  criteria schema is owned by the listener that evaluates it. A criteria bump
  needs a listener bump in the same change.
- Earning is idempotent at the DB level: a second `user_achievements` insert
  for the same `(userId, achievementId)` pair should be guarded by a unique
  index to prevent double-award on retry.
- `GET /achievements/my` and `/points` are deliberately alias routes — they
  exist for the front-end; do not remove them.
