---
title: Permission
type: entity
tags: [auth, security, rbac, type/entity]
status: living
updated: 2026-06-21
sources:
  - backend-spring/src/main/java/com/ulticode/modules/permission/
  - init-db/migrations/V20260610140000__Add_User_Permission_Expires_At.sql
aliases: [权限, RBAC]
---

# Permission

RBAC core: roles + per-user permissions with optional expiry. Resolves whether
the authenticated principal may do the thing. Enforced by Spring
`@PreAuthorize` across controllers.

## Key tables

- `user_permissions` — direct grants to a user (with `expires_at`, `V20260610140000`).
- `role_permissions` — what a role can do.

## How it's used

- `/admin/**` and privileged methods require `ADMIN` or `SUPER_ADMIN`.
- Spring `@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")` — privileged ops need it **even
  with a global route rule**.
- `@CheckBan` reads ban state; identity is read via `SecurityUtil.getCurrentUserId()`.
- Audit identity = the principal, never the request body.

## Source files

- `backend-spring/.../modules/permission/`; annotations in `.../common/annotation/` + `security/`.

## Cross-links

- [[entities/auth]] · [[entities/user]] · [[entities/moderation]]
- [[overview/auth-flow-overview]]

## Gotchas

- Permission checks must run server-side; client hiding is cosmetic only.
- `expires_at` lets grants lapse — the check must evaluate it, not just row existence.
