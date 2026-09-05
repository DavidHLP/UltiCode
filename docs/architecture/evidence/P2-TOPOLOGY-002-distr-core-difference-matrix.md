# P2-TOPOLOGY-002: Distributed/Core Config/Startup/Readiness Validation Matrix

## Overview

| Aspect | Distributed Mode | Core Mode |
|---|---|---|
| **Processes** | 5 owner services + 2 workers (Judge, Search) + 2 frontends | 1 process (`ulticode-core`) + Judge |
| **PM2 app names** | `ulticode-auth`, `ulticode-admin`, `ulticode-app`, `ulticode-submission`, `ulticode-notification`, `ulticode-judge`, `ulticode-search` | `ulticode-core`, `ulticode-judge` |
| **Startup command** | `./mvnw -f services/auth/pom.xml spring-boot:run` (per owner) | `./mvnw -f core/pom.xml spring-boot:run -Dspring-boot.run.profiles=core` |
| **Spring profile** | Per-owner (`auth`, `admin`, etc.) | `core` |
| **Owner contexts** | Each owner is a standalone Spring Boot process | 6 child contexts started in-process by `CoreOwnerContextManager` |
| **`CORE_OWNER_CONTEXTS_ENABLED`** | N/A (each owner is its own process) | `true` (default for Core profile) |
| **`CORE_JUDGE_REQUIRED`** | N/A | `true` (default; Core probes Judge readiness) |

## Configuration Differences

| Config Key | Distributed | Core |
|---|---|---|
| `spring.application.name` | `ulticode-auth` (etc.) per owner | `ulticode-core` (parent) + `ulticode-core-{owner}` per child |
| `spring.main.web-application-type` | `servlet` per owner | Parent: `servlet`; Child: `none` (`spring.main.web-application-type=none`) |
| `spring.datasource.*` | Each owner has its own DS (auth, admin, app, submission, notification) | Core passes per-owner DS via `core.datasource.{owner}.*` |
| `spring.flyway.enabled` | `true` per owner | `false` per child context |
| `dubbo.enabled` | `true` (services register with Nacos) | `false` (child contexts: in-process only) |
| `spring.redis.*` | Per-owner Redis (shared DB, per-owner username) | Core passes per-owner Redis via `{PREFIX}_REDIS_*` |
| Security | Full Admin/Security filter chains per owner | Parent: deny-all except `/api/v1/core/health/ready`; Children inherit owner-specific security |

## Startup Sequence

| Phase | Distributed | Core |
|---|---|---|
| 1 | Nacos, MySQL, Redis started via Compose | Same infra (mysql, redis, nacos) + meilisearch |
| 2 | Each owner Spring Boot app starts independently | Core parent context starts, then `CoreOwnerContextManager.startAll()` creates child contexts |
| 3 | Each owner registers Dubbo services with Nacos | Child contexts do NOT register Dubbo (`dubbo.enabled=false`); `CoreLocalAuthorizationMutationAdapter` bridges Core → Auth |
| 4 | Frontend apps connect to owner APIs | Same frontend → Core port 9108 |

## Readiness

| Surface | Distributed | Core |
|---|---|---|
| Auth | `http://9101/api/v1/auth/health/ready` | N/A (child context) |
| Admin | `http://9102/api/v1/admin/health/ready` | N/A (child context) |
| App | `http://9103/api/v1/app/health/ready` | N/A (child context) |
| Notification | `http://9105/api/v1/notification/health/ready` | N/A (child context) |
| Submission | `pm2` process check | N/A (child context) |
| Search | `http://9107` | N/A |
| **Core (Core profile)** | N/A | `http://9108/api/v1/core/health/ready` |

## Validation Entrypoints

| Validation Type | Distributed Command | Core Command |
|---|---|---|
| Static checks | `bash scripts/dev/test.sh static` | Same |
| Core profile contract | N/A | `bash scripts/test/core-profile-contract.sh` |
| Core smoke | N/A | `CORE_OWNER_CONTEXTS_ENABLED=false bash scripts/dev/test.sh core` |
| Owner unit tests | `./mvnw test -pl auth` (etc.) | `./mvnw test -pl core` |
| Contract tests | `./mvnw verify -pl auth-api` (etc.) | `./mvnw verify -pl api/auth-api` |
| Integration tests | `./mvnw -Dtest='*IT' test` | Same (Core has no `*IT`) |

## Key Difference: Cross-Owner Communication

| Communication Path | Distributed | Core |
|---|---|---|
| Admin → Auth mutation | `@DubboReference AuthorizationMutationService` (RPC via Nacos) | `CoreLocalAuthorizationMutationAdapter` (in-process delegation via `CoreOwnerContextManager.bean()`) |
| Admin → Auth query | `@DubboReference AccountQueryService` / `IdentityQueryService` (RPC) | `CoreLocalIdentityQueryAdapter` (in-process delegation) |
| Admin → App | `@DubboReference` (RPC) | **NOT SUPPORTED** — requires local adapter (see P1-CORE-002) |
| Submission → App | `@DubboReference` (RPC) | **NOT SUPPORTED** — `CoreLocalIdentityQueryAdapter` does not cover problem/title/user ports |
| Notification → Auth | `@DubboReference` (RPC) | **NOT SUPPORTED** — requires local adapter (see P1-CORE-002) |
| Submission → Auth | `@DubboReference` (RPC) | **NOT SUPPORTED** — requires local adapter (see P1-CORE-002) |

## Conclusion

Core mode provides **partial** cross-owner communication — only the mutation path
(Admin → Auth) is covered by the existing `CoreLocalAuthorizationMutationAdapter`,
plus the identity read path via the newly added `CoreLocalIdentityQueryAdapter`.
All other cross-owner paths (Admin → App, Submission → App/Auth, Notification →
Auth/App) remain uncovered. These require additional local adapters to be
production-viable.
