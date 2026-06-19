---
title: System Architecture
tags: [reference, architecture, living]
status: living
updated: 2026-06-19
owner: architect
generator: ecc:update-codemaps
---

# System Architecture

<!-- Generated: 2026-06-19 | Files scanned: 1301 (588 Java + 713 FE) | Token estimate: ~900 -->

## Topology

```
┌──────────────────────────────────────────────────────────────────────┐
│  Browser (PWA)                                                        │
│  ┌───────────────┐  ┌───────────────┐                                 │
│  │ console  :9002│  │management :9003│                                │
│  │ (user-facing) │  │ (admin only)   │                                │
│  │ Vue 3 + Vite  │  │ Vue 3 + Vite   │                                │
│  └───────┬───────┘  └────────┬───────┘                                │
│          │  /api/**           │  /api/**                              │
│          └────────┬───────────┘                                        │
│                   │  HttpOnly cookies (JWT + CSRF)                    │
│  ┌────────────────▼────────────────────────────────────────────────┐  │
│  │ ulticode-9001  Spring Boot 3.2.5 (Java 17)                      │  │
│  │   controller → service → mapper(MyBatis-Plus) → entity          │  │
│  │   security: JWT (jjwt 0.13) + Redis CSRF (Redisson)             │  │
│  │   realtime: STOMP WebSocket (cookie-auth only, ADR-008)         │  │
│  └──────┬───────────────┬───────────────┬────────────┬───────────────┘  │
└─────────┼───────────────┼───────────────┼────────────┼──────────────────┘
          │               │               │            │
   ┌──────▼──────┐ ┌──────▼──────┐ ┌──────▼──────┐ ┌───▼────────────┐
   │  MySQL 9.1  │ │  Redis 7    │ │ Nacos 2.3.2 │ │ Sandbox runner │
   │  :23306     │ │  :26379     │ │  :28848     │ │  (D-form,      │
   │  Flyway-mgd │ │  sessions/  │ │  config     │ │  ulticode-     │
   │  34 migs    │ │  CSRF/cache │ │  center     │ │  sandbox:lat.) │
   └─────────────┘ └─────────────┘ └─────────────┘ └────────────────┘
```

## Services & Ports (PM2)

| Port  | Process                | Type       | Notes                                              |
| ----- | ---------------------- | ---------- | -------------------------------------------------- |
| 9001  | `ulticode-9001`        | long-lived | Spring Boot REST + STOMP                           |
| 9002  | `ulticode-9002`        | long-lived | Console (Vite dev or preview)                      |
| 9003  | `ulticode-9003`        | long-lived | Management (Vite dev or preview)                   |
| 8563  | `ulticode-arthas`      | long-lived | Arthas 4.2.2 MCP (STATELESS, project-pinned)       |
| 1×    | `ulticode-init-db`     | one-shot   | Flyway migrate (exits after BUILD SUCCESS)          |

## Data Flow (high-traffic paths)

- **Submission judge**: console POST `/submissions` → `SubmissionService` → write `submissions` row + `judge_outbox` row (ADR-003) → background worker leases outbox → forks sandbox container → verdict callback → `submission_generation`/`submission_lease` cols guard exactly-once.
- **Contest live state**: `useContestSocket` (console) ← STOMP `/topic/contest/{id}` ← `ContestWebSocketService` (publishes on `contest_submissions` + ranking diff) → no-poll UI.
- **Auth**: `POST /auth/login` → bcrypt verify → set `access_token` + `refresh_token` (HttpOnly, Secure) cookies → Redis CSRF `csrf:{userId}:{tokenId}` (24h TTL + 5m grace) → `X-CSRF-Token` required on POST/PUT/DELETE/PATCH.
- **Notification delivery**: `notification_intents` → `notification_delivery_ledger` (per channel: email/in-app/push) → retry worker → user-facing badge updates via STOMP.
- **Moderation**: `POST /reports` → `moderation_queue` → admin `ModerationDashboardView` → action → `moderation_actions` + `user_bans` + `appeals` (binary state).

## Architecture Decisions (ADRs in `docs/adr/`)

- **ADR-001** verdict status codec (sandbox ↔ backend)
- **ADR-002** sandbox hexagonal refactor (D-form)
- **ADR-003** queue/outbox fencing (judge_outbox, lease)
- **ADR-004** notification intents (ledger-driven)
- **ADR-005/5a** rolling deploy + rollback drill
- **ADR-006** contest scoring engine activation
- **ADR-007** virtual contest lifecycle + rating isolation
- **ADR-008** WebSocket auth (cookie-only, no query token)
- **ADR-009** `isRated` gate + virtual rating isolation
- **ADR-010** cancel-state + virtual replay boundary
- **ADR-011** CRIT-6 shadow mode evaluation
- **ADR-012** extract auth UI components and view shells into `shared/auth-ui`

## Migration Strategy

`init-db/migrations/V*.sql` is the **single source of truth** (34 migrations as of 2026-06-19). Applied by `ulticode-init-db` PM2 one-shot before backend boot. Migrations are append-only; never edit applied migrations.

## Security Boundaries

- Backend 9001 exposed only via dev override (`docker-compose.dev.yml` binds 127.0.0.1)
- MySQL/Redis/Nacos bound loopback in dev; not published in base/prod compose
- All admin routes require `@RequireRole("ADMIN"|"SUPER_ADMIN")` + `@PreAuthorize`
- JWT secret ≥ 32 chars (`.env` injected); refresh tokens stored hash-only in DB
- OAuth state bound HttpOnly cookie, atomic consume in Redis
- WebSocket accepts `access_token` cookie only (ADR-008) — query/URL tokens rejected
