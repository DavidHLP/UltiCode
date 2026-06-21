---
title: Refresh Token
type: entity
tags: [auth, security, type/entity]
status: living
updated: 2026-06-21
sources:
  - backend-spring/src/main/java/com/ulticode/modules/refreshtoken/
  - init-db/migrations/V20260606130000__Secure_Refresh_Tokens_And_Lock_Seed_Accounts.sql
aliases: [刷新令牌]
---

# Refresh Token

Long-lived credential that mints new access JWTs. Stored **hash-only** in the DB —
the plaintext exists only transiently in the HttpOnly cookie; the stored row can
never be reversed into a usable token. Introduced by the 06-06 security migration.

> Rationale: [[concepts/refresh-token-hash-only-storage]]. Issuer: [[entities/auth]].

## Key table

- `refresh_tokens` — stores only the hash (+ user ref, rotates, revoked flag).

## Lifecycle

```
issue   → hash plaintext → store row → return plaintext once (HttpOnly cookie)
rotate  → verify hash of presented token → revoke old row → issue+hash new
revoke  → mark row revoked (logout / compromise)
```

The refresh endpoint (`/auth/refresh`) verifies the hash and **does not accept an
access token**.

## Migration

- `V20260606130000__Secure_Refresh_Tokens_And_Lock_Seed_Accounts` — moved to
  hash-only storage AND locked the demo seed accounts. **Must stay after any demo
  seed; never delete it.**

## Source files

- `backend-spring/.../modules/refreshtoken/` (entity, service, mapper).

## Cross-links

- [[entities/auth]] · [[entities/user]]
- [[concepts/refresh-token-hash-only-storage]] · [[concepts/security-invariants]]
- [[overview/auth-flow-overview]]

## Gotchas

- Comparing refresh tokens is hash-vs-hash, never plaintext lookup.
- "Revoke" is a row flag; the short-lived access JWT is stateless and expires on
  its own — refresh revocation stops *future* access, not the current one.
