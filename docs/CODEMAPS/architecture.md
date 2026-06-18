<!-- Generated: 2026-06-18 | Java 735 · Vue 821 · TS 482 · Migrations 34 · Controllers 43 | Token estimate: ~780 -->

# Architecture

## System Overview

```
┌─────────────┐  ┌─────────────┐
│  Console FE  │  │ Management  │  Vue 3 + Vite frontends
│  :9002       │  │ FE :9003    │
└──────┬───────┘  └──────┬──────┘
       │                 │
       └────────┬────────┘
                │ REST API (JWT + Redis session)
       ┌────────▼────────┐
       │  Spring Boot BE  │  :9001
       │  (26 modules)    │
       └──┬─────┬────┬───┘
          │     │    │
   ┌──────▼┐  ┌─▼──┐
   │ MySQL │  │Redis│  (no recommendation sub-system)
   │ :23306│  │:26379│
   └───────┘  └─────┘
            Nacos :28848 (config + discovery)
```

## Service Boundaries

| Service     | Port  | Stack                                            | Purpose                                            |
| ----------- | ----- | ------------------------------------------------ | -------------------------------------------------- |
| Backend     | 9001  | Spring Boot 3.2.5, Java 17, MyBatis-Plus 3.5.16  | Core API: auth, problems, contests, forum, notes, topics, etc. |
| Console     | 9002  | Vue 3.5, Vite 8, Pinia 3                         | User-facing frontend (personal dashboard refactor in progress) |
| Management  | 9003  | Vue 3.5, Vite 8, Pinia 3                         | Admin dashboard                                    |
| MySQL       | 23306 | MySQL 9.1                                        | Primary data store                                 |
| Redis       | 26379 | Redis 7                                          | Session (Redisson), rate limit, CSRF               |
| Nacos       | 28848 | Nacos 2.3.2                                      | Service discovery + config                         |
| Arthas MCP  | 8563  | Arthas 4.2.2 (wrapper, STATELESS MCP)            | Runtime JVM diagnostics (PM2 / hook / CLI, three-way mutex) |

> **Removed (2026-05-30)**: `recommend-provider` (:20881) and `recommend-web` (:9005)
> services + Dubbo RPC stack. The `recommendation` module is no longer active.
> Frontend `recommendation*` API/view files are still present — flagged as
> **orphan / dead code** for follow-up cleanup.

## Data Flow

- **Auth**: JWT access/refresh (HttpOnly cookies) → Redis session store → CSRF double-submit cookie
- **Submissions**: REST → in-process queue → sandbox execution (optional, `ulticode-sandbox:latest`) → MySQL
- **Real-time**: WebSocket STOMP on `/ws`, `/ws/contest`, `/ws/notifications` (cookie-based auth only)
- **Search**: MeiliSearch SDK 0.20.0 for problem/solution full-text (off by default in dev)
- **Reactions**: `edge_operations` enum extended with LIKE / DISLIKE / FAVORITE (V20260610150000) for viewer-reaction display
- **Notes & Topics**: per-user problem notes (1:1, V20260611141000) + solution topic taxonomy (V20260611140000)
- **Verdict delivery**: `judge_outbox` (V20260613100000, ADR-003) + submission generation counter / lease (V20260613110000) for the queue/outbox pattern
- **Notifications**: `notification_delivery_ledger` (V20260613120000) tracks per-channel delivery
- **Contest scoring**: scoring engine activation (ADR-006, V20260617120000) + virtual contest sessions / rating isolation (ADR-007, V20260617140000)
- **Audit**: `@Audited` annotation on privileged writes; persisted in `audit_logs`

## Migration Strategy

| Tool   | Location                  | Versions               | Purpose                       |
| ------ | ------------------------- | ---------------------- | ----------------------------- |
| Flyway | `init-db/migrations/`     | Timestamp-based (34 files) | Schema + seed migrations  |

Baseline: `V20260602_120000__Create_All_Tables.sql` (67 tables).
Security: `V20260606130000__Secure_Refresh_Tokens_And_Lock_Seed_Accounts.sql`.
Latest additions (2026-06-13 → 2026-06-17):
- `V20260613100000__Create_Judge_Outbox` + `V20260613110000__Add_Submission_Generation_And_Lease` (queue/outbox, ADR-003)
- `V20260613120000__Create_Notification_Delivery_Ledger` (per-channel delivery tracking)
- `V20260616120000__Add_Problem_Resource_Limits` (time/memory/cpus columns)
- `V20260617120000__Contest_Scoring_Hardening` + `V20260617130000__Contest_Slug_Unique` + `V20260617140000__Contest_Real_Unique_And_Session_Length` (ADR-006 scoring + ADR-007 virtual sessions)

## Shared Libraries

- `shared/auth-core/`      — Vue composable: types, cookie utils, CSRF manager, auth state, axios CSRF interceptor, permission checker
- `shared/badge-config/`   — Achievement/badge configuration (token-level config shared by both FEs)
- `shared/sandbox-types/`  — Shared sandbox DTO types (Phase 4, consumed by backend + sandbox harness)
- `shared/theme/`          — Theme tokens + Vue theme composable + `public/theme-bootstrap.js` source-of-truth (FOUC mitigation)
- `shared/design-system/`  — Legacy: `style.css` only (residual; consolidated under `shared/theme`)

## Runtime Tooling

- **PM2** (`ecosystem.config.cjs`): 5 apps — 4 long-running (`ulticode-9001`, `ulticode-9002`, `ulticode-9003`, `ulticode-arthas`) + one-shot `ulticode-init-db` migration task
- **Arthas MCP** (`scripts/start-arthas.sh`): three-launcher mutex (PM2 / SessionStart hook / CLI) on port 8563
- **MailHog** (optional, `SMTP_HOST=localhost:1025`): local email capture, off by default

## Recent Architectural Themes (2026-06-10 → 2026-06-18)

1. **Personal dashboard refactor** (console): 5 chart components reworked (LearningProgressChart, SkillRadarChart, SubmissionHistoryChart, UserProfileCard, UserStatsPanel); SidebarInset, AppLayout, DataTableToolbar, ProblemSetSidebar touched
2. **Component test coverage expansion** (console): new `__tests__/` under `components/common/data-table/` and `components/ui/sidebar/`
3. **New domain features**: problem notes (1:1 per user×problem), solution topics taxonomy
4. **Notification hardening**: column rename (reserved keyword) + logical delete
5. **Edge operations expansion**: viewer reaction tracking via enum extension
6. **Queue/outbox pattern** (ADR-003): `judge_outbox` + submission generation counter / lease for exactly-once verdict delivery
7. **Contest scoring engine** (ADR-006 / ADR-007): scoring-rule activation, contest slug/real unique constraints, virtual contest sessions with rating isolation
8. **Problem resource limits**: per-problem time/memory/cpus columns (V20260616120000)
9. **Notification delivery ledger**: per-channel delivery tracking (V20260613120000)
