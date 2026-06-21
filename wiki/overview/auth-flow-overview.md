---
title: Auth Flow Overview
type: overview
tags: [auth, security, map, type/overview]
status: living
updated: 2026-06-21
sources:
  - backend-spring/src/main/java/com/ulticode/modules/auth/
  - backend-spring/src/main/java/com/ulticode/modules/refreshtoken/
  - backend-spring/src/main/java/com/ulticode/modules/permission/
  - shared/auth-core/
  - CLAUDE.md
---

# Auth Flow Overview

> [!quote] Essence
> JWT access + refresh in HttpOnly cookies + Redis-backed CSRF + RBAC. Security
> invariants are non-negotiable — see [[concepts/security-invariants]].

## The tokens

| Token | Lifetime | Stored | Purpose |
|-------|----------|--------|---------|
| Access (JWT) | short | HttpOnly cookie | authenticates every API + WebSocket request |
| Refresh | long | HttpOnly cookie **+ DB as hash only** | mints new access tokens; the plaintext is never recoverable |

Both cookies are HttpOnly (no JS access). The refresh endpoint **does not accept**
an access token — see [[concepts/refresh-token-hash-only-storage]].

## Login → authenticated request

```
console                backend /auth                  Redis
  │  POST /auth/login     │                            │
  │  {user, pass}         │                            │
  ├──────────────────────►│ verify creds               │
  │                       │ issue access JWT           │
  │                       │ issue refresh → hash ─────►│ session + csrf
  │                       │  Set-Cookie: access        │
  │                       │  Set-Cookie: refresh        │
  │                       │  body: {csrfToken}         │
  │◄──────────────────────┘                            │
  │                                                     │
  │  POST /problems   Cookie: access, refresh           │
  │  X-CSRF-Token: <token>                              │
  ├──────────────────────►│ verify access JWT           │
  │                       │ verify CSRF (Redis) ──────►│
  │                       │  rotate CSRF token          │
  │                       │  X-New-CSRF-Token: <new>    │
  │◄──────────────────────┘                            │
```

## CSRF (mutating verbs only)

- Format `tokenId:tokenValue`, stored in Redis (`csrf:{userId}:{tokenId}`).
- `POST`/`PUT`/`PATCH`/`DELETE` require an `X-CSRF-Token` header.
- `GET`/`HEAD`/`OPTIONS` and **anonymous** requests are exempt.
- Token rotates per mutating request; 24h TTL + 5m grace.
- Frontend refresh of a stale token: `GET /auth/me` returns a fresh `csrfToken`.

Mechanism deep-dive: [[concepts/csrf-mechanism]]. Shared client handling lives in
`shared/auth-core`.

## Refresh rotation

```
POST /auth/refresh   Cookie: refresh (only)
  → verify refresh hash against DB (not access token!)
  → rotate: invalidate old, issue new refresh (hash stored), new access JWT
```

See [[entities/refreshtoken]] and [[concepts/refresh-token-hash-only-storage]].

## WebSocket auth

> [!danger] Auth contract
> - Authentication is **only** via the access-token cookie on the handshake.
> - **Forbidden**: query-string token, URL token, client-side STOMP auth header.
> - Rationale: keeping the credential out of URLs/logs — see [[concepts/security-invariants]].
>
> See [[entities/websocket]].

## Authorization (RBAC)

| Mechanism | Where | Example |
|-----------|-------|---------|
| `@PreAuthorize` (Spring) | controller methods | privileged ops, **even with a global route rule** |
| `@RequireRole("ADMIN")` | custom annotation | admin endpoints |
| `@CheckBan` | custom annotation | user-action endpoints |
| `@RateLimit` | custom annotation | sensitive ops (login, register) |
| `@Audited` | custom annotation | material state changes |
| `@CurrentUser` | param injection | resolve identity from principal |

**Invariant**: `/admin/**` and privileged methods require `ADMIN` or `SUPER_ADMIN`.
**Audit identity comes from the authenticated principal, never from the request
body.** Roles resolve through `permission` (`user_permissions` + `role_permissions`)
— see [[entities/permission]].

## Logout / revocation

Logout invalidates the refresh token (DB row) and clears cookies. Redis session +
CSRF entries are dropped. Access tokens are short-lived and stateless, so logout
does not "revoke" them instantly — the short TTL is the mitigation.

## Reading order

> [!link] Suggested path
> `entities/auth` → `concepts/csrf-mechanism` → `entities/refreshtoken` →
> `concepts/refresh-token-hash-only-storage` → `concepts/security-invariants` →
> `entities/permission`.