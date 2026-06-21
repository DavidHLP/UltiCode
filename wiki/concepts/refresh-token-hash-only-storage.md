---
title: Refresh Token Hash-Only Storage
type: concept
tags: [auth, security]
status: living
updated: 2026-06-21
sources:
  - backend-spring/src/main/java/com/ulticode/modules/refreshtoken/
  - init-db/migrations/V20260606130000__Secure_Refresh_Tokens_And_Lock_Seed_Accounts.sql
aliases: [刷新令牌哈希存储]
---

# Refresh Token Hash-Only Storage

## The problem
If a refresh token is stored in plaintext, a DB read (breach, backup leak, insider)
yields a reusable long-lived credential. The access JWT is short-lived and
stateless, so the refresh token is the high-value target.

## The decision
Store **only a hash** of the refresh token. The plaintext is returned to the
client once (HttpOnly cookie) and never persisted in recoverable form. Rotation
on every use: `verify hash → revoke old row → issue+hash new`. The refresh
endpoint **does not accept an access token**.

## Where it lives
- `modules/refreshtoken/` (entity stores the hash; service does hash-verify + rotate).
- Migration `V20260606130000` — converted to hash-only AND locked demo seed accounts.
  This migration **must stay after any demo seed; never delete it.**

## Trade-offs
- Can't display a user's token to them (feature, not bug).
- Rotate-on-use means a stolen-then-used token self-invalidates for the thief only
  if the legitimate user also rotates — short access TTL is the parallel mitigation.
- DB lookups are hash-vs-hash (compute hash of presented token, match).

## Related
[[entities/refreshtoken]] · [[entities/auth]] ·
[[concepts/security-invariants]] · [[overview/auth-flow-overview]]
