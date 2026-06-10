<!-- Generated: 2026-06-10 | Files scanned: 1923 (708 Java + 344 console Vue + 211 console TS + 476 mgmt Vue + 234 mgmt TS + others) | Token estimate: ~750 -->

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
| Backend     | 9001  | Spring Boot 3.2.5, Java 17, MyBatis-Plus 3.5.16  | Core API: auth, problems, contests, forum, etc.    |
| Console     | 9002  | Vue 3.5, Vite 8, Pinia 3                         | User-facing frontend                               |
| Management  | 9003  | Vue 3.5, Vite 8, Pinia 3                         | Admin dashboard                                    |
| MySQL       | 23306 | MySQL 9.1                                        | Primary data store                                 |
| Redis       | 26379 | Redis 7                                          | Session (Redisson), rate limit, CSRF, recommend-cache (legacy keys only) |
| Nacos       | 28848 | Nacos 2.3.2                                      | Service discovery + config                         |
| Arthas MCP  | 8563  | Arthas 4.1.9 (wrapper)                           | Runtime JVM diagnostics (PM2 / hook / CLI, three-way mutex) |

> **Note**: The previous `recommend-provider` (:20881) and `recommend-web` (:9005)
> services have been removed. The `recommendation` module and its Dubbo RPC
> stack are no longer part of the active backend.

## Data Flow

- **Auth**: JWT access/refresh (HttpOnly cookies) → Redis session store → CSRF double-submit cookie
- **Submissions**: REST → in-process queue → sandbox execution (optional, `ulticode-sandbox:latest`) → MySQL
- **Real-time**: WebSocket STOMP on `/ws`, `/ws/contest`, `/ws/notifications` (cookie-based auth only)
- **Search**: MeiliSearch SDK 0.20.0 for problem/solution full-text (off by default in dev)
- **Audit**: `@Audited` annotation on privileged writes; persisted in `audit_logs`

## Migration Strategy

| Tool   | Location                  | Versions               | Purpose                       |
| ------ | ------------------------- | ---------------------- | ----------------------------- |
| Flyway | `init-db/migrations/`     | Timestamp-based        | Schema + seed migrations      |

Baseline: `V20260602_120000__Create_All_Tables.sql` (67 tables). Latest security:
`V20260606130000__Secure_Refresh_Tokens_And_Lock_Seed_Accounts.sql`. Recent:
`V20260610120000__Create_Test_Cases_Table.sql`, `V20260610130000__Add_Test_Cases_Is_Deleted.sql`,
`V20260610140000__Add_User_Permission_Expires_At.sql`.

## Shared Libraries

- `shared/auth-core/`    — Vue composable: types, cookie utils, CSRF manager, auth state, axios CSRF interceptor, permission checker
- `shared/badge-config/` — Achievement/badge configuration (token-level config shared by both FEs)
- `shared/theme/`        — Theme tokens + Vue theme composable + `public/theme-bootstrap.js` source-of-truth (FOUC mitigation)
- `shared/design-system/` — Legacy: `style.css` only (residual; consolidated under `shared/theme`)

## Runtime Tooling

- **PM2** (`ecosystem.config.cjs`): 4 apps — `ulticode-9001`, `ulticode-9002`, `ulticode-9003`, `ulticode-arthas`
- **Arthas MCP** (`scripts/start-arthas.sh`): three-launcher mutex (PM2 / SessionStart hook / CLI) on port 8563
- **MailHog** (optional, `SMTP_HOST=localhost:1025`): local email capture, off by default
