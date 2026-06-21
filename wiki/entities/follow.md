---
title: Follow
type: entity
tags: [follow, social, user, type/entity]
status: living
updated: 2026-06-21
sources:
  - backend-spring/src/main/java/com/ulticode/modules/follow/
  - backend-spring/src/main/java/com/ulticode/modules/follow/controller/FollowController.java
  - backend-spring/src/main/java/com/ulticode/modules/follow/entity/UserFollow.java
aliases: [关注]
---

# Follow

Directed user-to-user follow graph. A lightweight social signal: who watches
whom, paginated lists, mutual-follow status. Lives at the `/users/{id}/follow*`
sub-routes so the path naturally belongs to the user profile.

## Responsibility

Maintain a single directed edge (`followerId → followingId`) and answer
read/follower/following queries. The follow module is intentionally small —
it is a graph, not a feed. Activity surface (what my followings are doing) is
built on top via [[entities/notification]] or [[entities/submission]].

## Key tables

| Table | Purpose |
|-------|---------|
| `user_follows` | composite-key row (`followerId`, `followingId`); the entity uses `IdType.INPUT` for `followerId` because the primary key is the follower, not a synthetic UUID |

The composite key is `(followerId, followingId)`; queries by either side use
the table's secondary indexes.

## Controllers

`FollowController` → mounted under `/users`:

| Endpoint | Notes |
|----------|-------|
| `POST /users/{id}/follow` | follow; returns updated `FollowStatsDTO` |
| `DELETE /users/{id}/follow` | unfollow |
| `GET /users/{id}/followers` | paginated followers |
| `GET /users/{id}/following` | paginated following list |
| `GET /users/{id}/follow/status` | whether current user follows `{id}` |

The controller takes the current user from `SecurityUtil.getCurrentUserId()` —
a user cannot follow themselves in practice, but the service should still
guard.

## Flow

`POST /users/{id}/follow` → `FollowService.follow(current, target)` → insert
into `user_follows` (idempotent) → recompute counts → return `FollowStatsDTO`.

## Source files

- `backend-spring/.../modules/follow/` (controller, service + impl, entity, dto, mapper).

## Cross-links

- [[entities/user]] (the follow routes are mounted under `/users`)
- [[overview/backend-modules-overview]]

## Gotchas

- `UserFollow.followerId` is `@TableId(type = IdType.INPUT)` because the
  composite key starts with the follower. The follow module assumes this; do
  not "fix" it to `ASSIGN_UUID`.
- The follow endpoint is **idempotent** — a second `POST` for the same pair
  must not 500; treat as a no-op (or return current state).
- The "mutual follow" / "friends" concept is **not** in this module; compute
  it as a query (exists-in-both-directions) on demand.
- The follow routes are mounted under `/users/{id}/follow*` — collisions with
  [[entities/user|other user routes]] are avoided because `follow` is a path
  segment, not a UUID slot.
