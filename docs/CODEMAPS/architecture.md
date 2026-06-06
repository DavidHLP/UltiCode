<!-- Generated: 2026-06-06 | Files scanned: 1688 (341 console Vue + 474 mgmt Vue + 667 Java + others) | Token estimate: ~750 -->

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

> **Note**: The previous `recommend-provider` (:20881) and `recommend-web` (:9005)
> services have been removed. The `recommendation` module and its Dubbo RPC
> stack are no longer part of the active backend. The base `recommendation/`
> module is also absent from `backend-spring/src/main/java/com/ulticode/modules/`.

## Data Flow

- **Auth**: JWT access/refresh (HttpOnly cookies) → Redis session store → CSRF double-submit cookie
- **Submissions**: REST → in-process queue → sandbox execution → MySQL
- **Real-time**: WebSocket STOMP on `/ws`, `/ws/contest`, `/ws/notifications` (cookie-based auth only)
- **Search**: MeiliSearch SDK 0.20.0 for problem/solution full-text
- **Audit**: `@Audited` annotation on privileged writes; persisted in `audit_logs`

## Migration Strategy

| Tool   | Location                  | Versions               | Purpose                       |
| ------ | ------------------------- | ---------------------- | ----------------------------- |
| Flyway | `init-db/migrations/`     | Timestamp-based        | Schema + seed migrations      |

Baseline: `V20260602_120000__Create_All_Tables.sql` (67 tables). Latest security:
`V20260606130000__Secure_Refresh_Tokens_And_Lock_Seed_Accounts.sql`.

## Shared Libraries

- `shared/auth-core/`    — Vue composable: types, cookie utils, CSRF manager, auth state, axios CSRF interceptor, permission checker
- `shared/badge-config/` — Achievement/badge configuration (token-level config shared by both FEs)
- `shared/design-system/` — Design tokens, Tailwind theme preset, shared Vue components
