<!-- Generated: 2026-06-12 | Java 735 · Vue 821 · TS 482 · Migrations 25 · Controllers 43 | Token estimate: ~780 -->

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
| Arthas MCP  | 8563  | Arthas 4.1.9 (wrapper)                           | Runtime JVM diagnostics (PM2 / hook / CLI, three-way mutex) |

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
- **Notes & Topics**: per-user problem notes (1:1, V20260611_140000) + solution topic taxonomy (V20260611140000)
- **Audit**: `@Audited` annotation on privileged writes; persisted in `audit_logs`

## Migration Strategy

| Tool   | Location                  | Versions               | Purpose                       |
| ------ | ------------------------- | ---------------------- | ----------------------------- |
| Flyway | `init-db/migrations/`     | Timestamp-based (25 files) | Schema + seed migrations  |

Baseline: `V20260602_120000__Create_All_Tables.sql` (67 tables).
Security: `V20260606130000__Secure_Refresh_Tokens_And_Lock_Seed_Accounts.sql`.
Latest additions (2026-06-10 → 2026-06-12):
- `V20260610150000__Extend_Edge_Operations_For_Problem_Reactions` (enum expand)
- `V20260611120000__Rename_Notification_Pref_System_Column` (MySQL 9.x reserved keyword)
- `V20260611130000__Add_Notifications_Is_Deleted` (logical delete)
- `V20260611_140000__Create_Problem_Notes_Table` (new feature table)
- `V20260611140000__Create_Solution_Topics_Table` (BUG-01 fix, 8 seeded topics)

## Shared Libraries

- `shared/auth-core/`     — Vue composable: types, cookie utils, CSRF manager, auth state, axios CSRF interceptor, permission checker
- `shared/badge-config/`  — Achievement/badge configuration (token-level config shared by both FEs)
- `shared/theme/`         — Theme tokens + Vue theme composable + `public/theme-bootstrap.js` source-of-truth (FOUC mitigation)
- `shared/design-system/` — Legacy: `style.css` only (residual; consolidated under `shared/theme`)

## Runtime Tooling

- **PM2** (`ecosystem.config.cjs`): 4 apps — `ulticode-9001`, `ulticode-9002`, `ulticode-9003`, `ulticode-arthas`
- **Arthas MCP** (`scripts/start-arthas.sh`): three-launcher mutex (PM2 / SessionStart hook / CLI) on port 8563
- **MailHog** (optional, `SMTP_HOST=localhost:1025`): local email capture, off by default

## Recent Architectural Themes (2026-06-10 → 2026-06-12)

1. **Personal dashboard refactor** (console): 5 chart components reworked (LearningProgressChart, SkillRadarChart, SubmissionHistoryChart, UserProfileCard, UserStatsPanel); SidebarInset, AppLayout, DataTableToolbar, ProblemSetSidebar touched
2. **Component test coverage expansion** (console): new `__tests__/` under `components/common/data-table/` and `components/ui/sidebar/`
3. **New domain features**: problem notes (1:1 per user×problem), solution topics taxonomy
4. **Notification hardening**: column rename (reserved keyword) + logical delete
5. **Edge operations expansion**: viewer reaction tracking via enum extension
