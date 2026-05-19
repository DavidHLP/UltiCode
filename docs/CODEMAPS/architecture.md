<!-- Generated: 2026-05-19 | Files scanned: 2106 | Token estimate: ~800 -->

# Architecture

## System Overview

```
┌─────────────┐  ┌─────────────┐
│  Console     │  │  Management  │
│  :9002       │  │  :9003       │
│  (Vue 3)     │  │  (Vue 3)     │
└──────┬───────┘  └──────┬───────┘
       │                 │
       └────────┬────────┘
                │  HTTP / WebSocket
       ┌────────▼────────┐
       │  Backend Spring  │
       │  :9001           │
       │  (Spring Boot)   │
       └──┬─────┬────┬───┘
          │     │    │
   ┌──────▼┐  ┌─▼──┐ ┌▼──────────────┐
   │ MySQL │  │Redis│ │ Recommendation │
   │ :23306│  │:26379│ │ (Dubbo+Spark) │
   └───────┘  └─────┘ └──┬──────┬─────┘
                         │      │
                    ┌────▼┐ ┌───▼──┐
                    │:9005│ │:20881│
                    │ Web │ │Dubbo │
                    └─────┘ └──────┘
```

## Service Boundaries

| Service | Port | Role |
|---------|------|------|
| backend-spring | 9001 | REST API, auth, WebSocket |
| console | 9002 | User-facing SPA (548 files) |
| management | 9003 | Admin dashboard SPA (709 files) |
| recommend-provider | 20881 | Dubbo RPC provider |
| recommend-web | 9005 | Recommendation REST gateway |

## Data Flow

- **Auth**: JWT + Redis session. CSRF token via double-submit cookie.
- **Real-time**: WebSocket (STOMP) for contest ranking, notifications.
- **Recommendation**: Backend calls Dubbo RPC → recommend-provider → Spark batch features.
- **DB Migrations**: Flyway-managed via `db-manager/` CLI (Python).

## Shared Libraries

- `shared/auth-core`: Vue composable — auth state, CSRF, permission checks, cookie utils. Consumed by both frontends.

## Infrastructure

- **Service Discovery**: Nacos 2.3.2 (:28848)
- **Containerization**: Docker Compose (MySQL 9.1, Redis 7, Nacos)
- **Process Manager**: PM2 (`ecosystem.config.cjs`)
- **CI**: GitHub Actions (path-based change detection)
