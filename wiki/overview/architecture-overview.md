---
title: Architecture Overview
type: overview
tags: [architecture, map, type/overview]
status: living
updated: 2026-06-21
sources:
  - AGENTS.md
  - CLAUDE.md
  - backend-spring/
  - console/
  - management/
  - shared/
  - init-db/
  - docker/
---

# Architecture Overview

> [!quote] Essence
> Three deployable apps (backend, console, management) + shared libraries
> (`shared/auth-core`, `shared/theme`) + Flyway migrations + D-form sandbox.

UltiCode is an online judge: users solve programming problems, submit code, and
get it judged in an isolated sandbox; contests, a forum, achievements, and a
notification system sit on top.

## System map

```
                    ┌─────────────────────────────────────────────┐
   browser ───────► │  console/        (Vue 3, port 9002)          │  user-facing
                    │  management/     (Vue 3, port 9003)          │  admin-facing
                    └───────────────┬─────────────────────────────┘
                                    │  HTTPS, JWT cookie + CSRF
                    ┌───────────────▼─────────────────────────────┐
                    │  backend-spring/  (Spring Boot 3.2.5, :9001) │
                    │  26 modules  controller→service→mapper→entity│
                    └───┬──────────┬──────────────┬───────────┬────┘
                        │          │              │           │
              ┌─────────▼──┐  ┌────▼─────┐  ┌─────▼────┐  ┌────▼─────────┐
              │ MySQL 9.1  │  │ Redis 7  │  │ Nacos    │  │ sandbox      │
              │   :23306   │  │ :26379   │  │ :28848   │  │ (D-form)     │
              │ Flyway     │  │ session, │  │ config/  │  │ docker image │
              │ migrations │  │ CSRF,    │  │ service  │  │ per-submit   │
              │            │  │ streams  │  │ discovery│  │ C/C++/Java/  │
              │            │  │ queue    │  │          │  │ JS/Python    │
              └────────────┘  └──────────┘  └──────────┘  └──────────────┘
```

Shared code lives outside the apps: `shared/auth-core` (cookie/CSRF/auth-state/
permission composable) and `shared/theme` (ThemeMode + LXGW WenKai typography).
`init-db/` owns Flyway migrations. `docker/sandbox/` is the judge image.

## The pieces

| Piece | Path | Role | Port |
|-------|------|------|------|
| Backend API | `backend-spring/` | Spring Boot 3.2.5, Java 17, MyBatis-Plus | 9001 |
| User frontend | `console/` | Vue 3.5 + TS, Vite, Pinia | 9002 |
| Admin frontend | `management/` | Vue 3.5 + TS, Vite, Pinia | 9003 |
| Shared auth | `shared/auth-core/` | cookie / CSRF / auth-state / permission | — |
| Shared theme | `shared/theme/` | ThemeMode, `applyThemeToDOM`, typography | — |
| DB migrations | `init-db/` | Flyway `V*.sql`, `flyway.conf` | — |
| Judge sandbox | `docker/sandbox/` | D-form harness image, 4 languages | — |

Deep maps: [[overview/backend-modules-overview|backend modules]] ·
[[overview/frontend-apps-overview|frontend apps]].

## Request lifecycle (a submission)

1. User submits code in `console/` → `POST /problems/{id}/submissions`
   (auth via JWT access cookie; CSRF header on the mutating verb).
2. `submission` module writes a `Submission` row, enqueues a `JudgeJob` through the
   `queue` module's outbox → Redis Streams. See [[overview/judging-pipeline-overview]].
3. A worker pulls the job, spins up the sandbox container, runs each test case,
   and writes the verdict back. A `SubmissionJudgedEvent` fires.
4. `notification` records an intent, delivers via the ledger, and pushes over
   WebSocket (`websocket` module) to the user's open tab.
5. If inside a contest, `contest` recomputes standings (`contest_problem_results`,
   `first_solve_records`, `global_rankings`).

Auth details: [[overview/auth-flow-overview]]. Data model:
[[overview/database-schema-overview]]. Local setup:
[[overview/dev-environment-overview]].

## Module layering (backend)

Every backend module follows the same shape (see `.claude/rules/backend/springboot-rules.md`):

```
modules/<module>/
├── controller/   REST endpoints, @PreAuthorize, param binding
├── service/      business logic (interface + impl/)
├── mapper/       MyBatis-Plus BaseMapper, @Select/@Update
├── entity/       DO classes, @TableName, UUID PK, soft-delete
└── dto/          DTO/VO (MapStruct-mapped)
```

Controllers never call mappers directly; services never return entities to
controllers. Responses are wrapped in `Result<T>` (see
`backend-spring/.../common/response/Result.java`).

## Entry points for reading the code

- **API surface**: `backend-spring/.../modules/*/controller/*Controller.java` —
  each `@RequestMapping` prefix is in [[overview/backend-modules-overview]].
- **Boot + config**: `backend-spring/.../UlticodeApplication.java`,
  `common/config/`, `application.yml`.
- **Security**: `security/` package (JWT filter, CSRF filter, access/refresh
  cookie handlers).
- **Schema**: `init-db/migrations/V20260602_120000__Create_All_Tables.sql` (baseline)
  + the 06-06/06-13 hardening migrations.

## Tech stack (cheat sheet)

| Layer | Tech |
|-------|------|
| Backend | Spring Boot 3.2.5, Java 17, MyBatis-Plus 3.5.16, MapStruct 1.6.3 |
| Auth | JWT (jjwt 0.13.0), Redis session (Redisson 4.3.1) |
| DB | MySQL 9.1, Redis 7, Nacos 2.3.2 |
| Frontend | Vue 3.5, TS, Vite 8, Pinia 3, Vue Router 5, Tailwind v4, shadcn-vue |
| Docs/API | SpringDoc OpenAPI 2.6.0 |
| i18n | vue-i18n 11 |

Operational specifics (PM2, Arthas, the sandbox image build) live in
`AGENTS.md` and `CLAUDE.md`; this page is the map, not the runbook.

## Links out

> [!link] Related pages
> - [[overview/backend-modules-overview]] · [[overview/frontend-apps-overview]]
> - [[overview/judging-pipeline-overview]] · [[overview/auth-flow-overview]]
> - [[overview/database-schema-overview]] · [[overview/dev-environment-overview]]