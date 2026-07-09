---
title: Auth
type: entity
tags: [auth, security, core, type/entity]
status: living
updated: 2026-06-21
sources:
  - backend-spring/src/main/java/com/ulticode/modules/auth/
  - backend-spring/src/main/java/com/ulticode/security/
  - shared/auth-core/
aliases: [认证, 登录]
---

# Auth

The login/logout and token-issuance surface. `auth` is the entry point; the
credentials machinery (refresh token store) is [[entities/refreshtoken]], and the
filter chain + annotations live in the `security/` package.

> Full flow: [[overview/auth-flow-overview]]. Invariants: `AGENTS.md` § Security Invariants.

## Responsibility

Owns login, logout, access/refresh token issuance, and CSRF token mint/rotate.
Collaborates with `security/` (filters, cookie handlers) and
[[entities/refreshtoken]] (storage).

## Key flows

```
login   → verify creds → issue access JWT (HttpOnly cookie)
                       → issue refresh (HttpOnly cookie) → store hash via refreshtoken
                       → mint CSRF (Redis) → return in body
refresh → verify refresh hash (NO access token accepted) → rotate → new access
logout  → revoke refresh row → clear cookies → drop Redis session/CSRF
me      → GET /auth/me → identity + fresh CSRF
```

## Controllers

- `AuthController` → `/auth` (`/login`, `/logout`, `/refresh`, `/me`, OAuth callbacks).

## The `security/` package (collaborators)

- JWT filter (access cookie verification on every request).
- CSRF filter (Redis `tokenId:tokenValue` verify + rotate on mutating verbs).
- Access/refresh cookie handlers.
- Annotations: `@CheckBan`, `@RateLimit`, `@Audited`. Role enforcement uses Spring's `@PreAuthorize`; identity is read via `SecurityUtil.getCurrentUserId()`.
- OAuth: state bound to HttpOnly cookie, Redis atomic consume.

## CSRF

Minted and rotated here; the mechanism and Redis key shape are documented in
`CLAUDE.md` § CSRF Mechanism. Frontend handling shared via `shared/auth-core`.

## Source files

- `backend-spring/.../modules/auth/` (controller, service).
- `backend-spring/.../security/` (filters, annotations, cookie handlers).
- `shared/auth-core/` (client-side cookie/CSRF/auth-state).

## Cross-links

- [[entities/refreshtoken]] · [[entities/permission]] · [[entities/websocket]]
- [[overview/auth-flow-overview]]

## Gotchas

- The refresh endpoint **must not** accept an access token — that's the
  refresh-token theft mitigation. See `AGENTS.md` § Security Invariants.
- Access tokens are stateless and short-lived; logout doesn't instantly kill them
  — the short TTL is the mitigation, not a revocation list.
- OAuth state must be consumed atomically from Redis; replay = rejection.
