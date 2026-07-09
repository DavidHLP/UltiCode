---
title: WebSocket
type: entity
tags: [websocket, realtime, security, type/entity]
status: living
updated: 2026-06-21
sources:
  - backend-spring/src/main/java/com/ulticode/modules/websocket/
  - backend-spring/src/main/java/com/ulticode/security/
aliases: [实时通信]
---

# WebSocket

Real-time push transport: notification delivery, contest standing ticks, online
presence. Auth is **access-cookie-only** on the handshake — the credential never
appears in URLs or client STOMP frames.

> Delivers for: [[entities/notification]]. Cookie-only auth invariant lives in
> `AGENTS.md` § Security Invariants.

## Responsibility

Owns the STOMP/WebSocket endpoint, session registry (online list), and the
subscription channels for per-user notifications and contest updates.

## Auth

- Handshake authenticates via the **access-token cookie** only.
- **Forbidden**: query-string token, URL token, client-side STOMP `auth` frame.
- Rationale: keep the JWT out of logs/URLs — see `AGENTS.md` § Security Invariants.

## Flows

- `notification` delivers via a per-user channel (deduped by the ledger first).
- `contest` pushes standing changes to contest subscribers.
- Online presence tracked in the session registry.

## Source files

- `backend-spring/.../modules/websocket/`; auth cooperation in `.../security/`.

## Cross-links

- [[entities/notification]] · [[entities/auth]]

## Gotchas

- Push must be idempotent on the client — the ledger dedupes server-side, but
  reconnects can still replay frames.
- Never add a query-token fallback "for convenience"; it breaks the URL-credential
  invariant.
