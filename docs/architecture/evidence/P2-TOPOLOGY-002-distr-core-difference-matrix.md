# P2-TOPOLOGY-002: Distributed/Core Config/Startup/Readiness Validation Matrix

## Overview

| Aspect | Distributed Mode | Core Mode |
|---|---|---|
| **Processes** | 5 Owner services + 2 Workers (Judge, Search) + frontends as selected | 1 `ulticode-core` parent + independent Judge |
| **PM2 app names** | Owner/Worker apps selected by the named scope | `ulticode-core`, `ulticode-judge` |
| **Startup command** | Per-owner `spring-boot:run` through the DevStack resolver | `spring-boot:run -Dspring-boot.run.profiles=core` |
| **Spring profile** | Per-owner (`auth`, `admin`, etc.) | `core` |
| **Owner contexts** | Each Owner is a standalone process | Auth and Admin enabled; App, Submission, Notification, Search registered but disabled |
| **`CORE_OWNER_CONTEXTS_ENABLED`** | N/A | `false` in generic config/PM2 defaults; named `core` scope sets `true` |
| **`CORE_JUDGE_REQUIRED`** | N/A | `false` in generic config/PM2 defaults and named `core` scope |

## Configuration differences

| Config key | Distributed | Core |
|---|---|---|
| `spring.application.name` | Per-owner process name | `ulticode-core` parent + `ulticode-core-{owner}` child |
| `spring.main.web-application-type` | `servlet` per Owner | Parent `servlet`; child `none` |
| `spring.datasource.*` | Each Owner owns its process-local DS | Parent has five factories; enabled child startup receives owner DS properties |
| `spring.flyway.enabled` | `true` in the Owner migration path | `false` in each child |
| `dubbo.enabled` | `true`, Nacos registration | `false` in each child; local contracts are registered explicitly |
| Redis | Per-owner credentials/namespace | Enabled child receives its owner Redis properties |
| Security | Owner HTTP/WS policy | Parent permits readiness only and denies all other requests |

## Startup and readiness

| Phase | Distributed | Core |
|---|---|---|
| 1 | Named scope starts required Compose infrastructure | Named `core` scope may start required infra; `test.sh core` starts none |
| 2 | Each Owner starts independently | `CoreOwnerContextManager` starts only registry-enabled children |
| 3 | Owners register Dubbo with Nacos | Children do not register Dubbo; Admin receives explicit local Auth contracts |
| 4 | Frontends call Owner routes | Core has no business HTTP/WS aggregation route; only readiness is exposed |

| Surface | Distributed | Core |
|---|---|---|
| Auth/Admin/App/Notification | Owner readiness and business routes | N/A while child contexts are not exposed as HTTP |
| Submission/Judge/Search | Worker/Owner readiness by named scope | N/A except independent Judge process |
| **Core** | N/A | `http://9108/api/v1/core/health/ready` |

## Validation entrypoints

| Validation type | Distributed command | Core command |
|---|---|---|
| Static checks | `bash scripts/dev/test.sh static` | Same |
| Core profile contract | N/A | `bash scripts/test/core-profile-contract.sh` |
| Core parent smoke | N/A | `bash scripts/dev/test.sh core` (contexts disabled) |
| Owner unit tests | Owner module `-Punit` tests | Core module `-Punit` tests |
| Contract tests | Relevant `services/api` contract gates | Same shared contract gates |
| Enabled-owner wiring | Disposable named Owner scope | Not run; requires disposable Owner artifacts/infra |
| Business journey | Disposable `app-journey` scope | Not available: Core exposes readiness only |

## Cross-owner communication

| Communication path | Distributed | Core |
|---|---|---|
| Admin → Auth mutation | Dubbo `AuthorizationMutationService` | `CoreLocalAuthorizationMutationAdapter` |
| Admin → Auth identity | Dubbo `IdentityQueryService` | Explicitly registered `CoreLocalIdentityQueryAdapter` |
| Admin → App | Dubbo contract | Not supported |
| Submission → App/Auth | Dubbo contracts | Not supported |
| Notification → Auth/App | Dubbo contracts | Not supported |

Core therefore proves only bounded parent assembly plus the explicitly
registered Admin→Auth local contract seam. It does not claim parity with the
distributed business surface.
