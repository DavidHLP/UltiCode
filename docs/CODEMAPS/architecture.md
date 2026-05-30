<!-- Generated: 2026-05-30 | Files scanned: 1964 | Token estimate: ~750 -->

# Architecture

## System Overview

```
┌─────────────┐  ┌─────────────┐
│  Console FE  │  │ Management  │  Vue 3 + Vite frontends
│  :9002       │  │ FE :9003    │
└──────┬───────┘  └──────┬──────┘
       │                 │
       └────────┬────────┘
                │ REST API
       ┌────────▼────────┐
       │  Spring Boot BE  │  :9001
       │  (JWT + Redis)   │
       └──┬─────┬────┬───┘
          │     │    │
   ┌──────▼┐  ┌─▼──┐ ┌▼──────────┐
   │ MySQL │  │Redis│ │ Recommend │
   │ :23306│  │:26379│ │ (Dubbo)   │
   └───────┘  └─────┘ └─┬─────┬──┘
                         │     │
              ┌──────────▼┐ ┌──▼──────────┐
              │ Provider   │ │ Web API     │
              │ :20881     │ │ :9005       │
              └────────────┘ └─────────────┘
                         Nacos :28848 (discovery)
```

## Service Boundaries

| Service | Port | Stack | Purpose |
|---------|------|-------|---------|
| Backend | 9001 | Spring Boot 3.2.5, Java 17 | Core API (auth, problems, contests, forum, moderation, submissions, solutions, etc.) |
| Console | 9002 | Vue 3.5, Vite 8, Pinia 3 | User-facing frontend |
| Management | 9003 | Vue 3.5, Vite 8, Pinia 3 | Admin dashboard |
| Recommend Provider | 20881 | Spring Boot, Dubbo 3.2.14 | Dubbo RPC service (recall + ranking) |
| Recommend Web | 9005 | Spring Boot | REST gateway for recommendations |
| MySQL | 23306 | MySQL 9.1 | Primary data store |
| Redis | 26379 | Redis 7 | Session cache, rate limiting, recommendation cache |
| Nacos | 28848 | Nacos 2.3.2 | Service discovery (Dubbo) |

## Data Flow

- **Auth**: JWT access/refresh tokens → Redis session store → CSRF double-submit cookie
- **Submissions**: REST → Queue (in-process) → Sandbox execution → Result stored in MySQL
- **Recommendations**: User features → Recall (CF/Hot/Content/ColdStart) → Rank (RuleRankStrategy) → Re-rank (Diversity/Freshness) → Redis cache → REST/Dubbo
- **Real-time**: WebSocket STOMP on `/ws`, `/ws/contest`, `/ws/notifications` → broker prefixes `/topic`, `/queue`, `/user`
- **Search**: MeiliSearch SDK 0.20.0 for problem/solution full-text search

## Migration Strategy (Dual Flyway)

| Tool | Location | Versions | Purpose |
|------|----------|----------|---------|
| db-manager/ | `db-manager/migrations/` | V1–V108 (sequential) | Incremental schema changes |
| init-db/ | `init-db/migrations/` | V{YYYYMMDDHHMMSS} (timestamp) | Baseline from existing DB |

## Shared Libraries

- `shared/auth-core/` — Vue composable: types, cookie utils, CSRF manager, auth state machine, axios CSRF interceptor, permission checker (TS ~5.9, pkg v0.0.1)
- `db-manager/` — Flyway migration CLI (Python), 42 migrations (V1–V108)
- `init-db/` — Standalone Flyway with Maven, timestamp-based versioning, baseline migration