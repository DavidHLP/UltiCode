---
title: Security Invariants
type: concept
tags: [security, meta]
status: living
updated: 2026-06-21
sources:
  - CLAUDE.md
  - backend-spring/src/main/java/com/ulticode/security/
aliases: [安全不变量]
---

# Security Invariants

Non-negotiable rules. Any change touching auth, deployment secrets, seed
accounts, or network exposure must respect **all** of these. The authoritative
list lives in `CLAUDE.md` § Security Invariants; this page is the wiki's
cross-linked index of them.

## The invariants

1. **Credentials** — never hardcoded or committed. Runtime secrets come from
   `.env` / CI secrets / deployment key store.
2. **JWT secret** ≥ 32 characters.
3. **Access + refresh** both in **HttpOnly** cookies.
4. **Refresh token** — DB **hash-only**, plaintext unrecoverable; the refresh
   endpoint **does not accept** an access token. ([[concepts/refresh-token-hash-only-storage]])
5. **OAuth state** — bound to an HttpOnly browser cookie, consumed atomically in Redis.
6. **WebSocket auth** — **only** the `access_token` cookie; query-string / URL /
   client STOMP tokens are forbidden. ([[entities/websocket]])
7. **Admin surface** — `/admin/**` and privileged methods require `ADMIN` or
   `SUPER_ADMIN`; **audit identity comes from the principal, never the request body**. ([[entities/permission]])
8. **Ports** — base/production compose publishes **no** MySQL/Redis/Nacos/backend
   ports; only `docker-compose.dev.yml` exposes infra, bound to `127.0.0.1`.
9. **Nacos** — auth stays on; the default `nacos/nacos` account stays disabled.
10. **UGC rendering** — Markdown / KaTeX output **must** be sanitized before `v-html`.
11. **Theme bootstrap** — `console|management/public/theme-bootstrap.js` is the only
    place theme init runs (FOUC). Under a strict future CSP it needs a nonce/hash.

## Where it's enforced
- `security/` package (JWT filter, CSRF filter, cookie handlers, annotations).
- Docker compose files (port binding).
- Migration `V20260606130000` (refresh hash-only + seed lock).

## Related
[[entities/auth]] · [[entities/refreshtoken]] · [[entities/permission]] ·
[[entities/websocket]] · [[concepts/csrf-mechanism]] ·
[[concepts/refresh-token-hash-only-storage]] · [[concepts/sandbox-security-contract]]
