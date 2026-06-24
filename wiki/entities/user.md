---
title: User
type: entity
tags: [user, core, type/entity]
status: living
updated: 2026-06-21
sources:
  - backend-spring/src/main/java/com/ulticode/modules/user/
  - backend-spring/src/main/java/com/ulticode/modules/user/entity/User.java
aliases: [用户]
---

# User

The account entity. Profile, settings, rating/standing. Identity itself (login,
tokens, roles) is split across [[entities/auth]], [[entities/refreshtoken]], and
[[entities/permission]] — `user` owns the **profile**, not the credentials.

## Responsibility

Owns registration, profile read/update, user settings, and rating/standing
computation (contest-driven).

## Key tables

- `users` — profile, display name, avatar, rating, status.
- (identity lives in `refresh_tokens`, `user_permissions` — separate modules.)
- (social graph in `user_follows` — `follow` module.)

## Key flows

- **Register** — creates the `users` row; credentials handled by [[entities/auth]].
  `@RateLimit` on the endpoint (login/register window).
- **Profile** — read/update via `/users`.
- **Settings** — preferences (notification prefs delegate to [[entities/notification]]).
- **Rating** — updated from contest results (`contest/global_rankings`).

## Controllers

- `UserController` → `/users` (profile, settings).

## Source files

- `backend-spring/.../modules/user/` (controller, service, entity, dto).

## Cross-links

- [[entities/auth]] · [[entities/refreshtoken]] · [[entities/permission]]
- [achievement / follow modules](../overview/backend-modules-overview.md) (covered in module table)
- [[entities/achievement]] · [[entities/follow]] · [[entities/subscription]]
- [[overview/auth-flow-overview]]

## Gotchas

- Audit identity on writes comes from the authenticated principal, **never** from
  a request body field — see [[concepts/security-invariants]].
- Rating is derived data; don't write it from arbitrary paths — only the contest
  finalization flow.
- `@CheckBan` guards user-action endpoints; a banned user's profile is still
  readable but actions are blocked.
