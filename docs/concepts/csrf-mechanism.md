---
title: CSRF Mechanism
type: concept
tags: [auth, security]
status: living
updated: 2026-06-21
sources:
  - backend-spring/src/main/java/com/ulticode/security/
  - shared/auth-core/
  - CLAUDE.md
aliases: [CSRF]
---

# CSRF Mechanism

## The problem
A browser auto-attaches cookies to cross-site requests, so a logged-in user can
be tricked into making a state-changing `POST`/`PUT`/`DELETE` from another origin.
CSRF tokens break that: an attacker can't read the token (same-origin policy) so
can't forge the header.

## The decision
Redis-backed double-submit token, shape `tokenId:tokenValue`.

- Stored at `csrf:{userId}:{tokenId}` in Redis.
- Mutating verbs (`POST`/`PUT`/`PATCH`/`DELETE`) require the `X-CSRF-Token` header.
- `GET`/`HEAD`/`OPTIONS` and **anonymous** requests are exempt.
- Token **rotates** per mutating request; the new one returns as `X-New-CSRF-Token`.
- 24h TTL + 5m grace.
- Frontend refresh of a stale token: `GET /auth/me` returns a fresh `csrfToken`.

## Where it lives
- `security/` CSRF filter (verify + rotate).
- `shared/auth-core` (client attach + rotate handling).

## Trade-offs
- One extra Redis lookup per mutating request — cheap.
- Stateless JWT + stateful CSRF is intentional: JWT authenticates, CSRF authorizes
  the verb's intent.

## Related
[[entities/auth]] · [[concepts/security-invariants]] · [[overview/auth-flow-overview]]
