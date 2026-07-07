# Backend Spring Boot AGENTS.md

> **Part of**: UltiCode (see [root AGENTS.md](../AGENTS.md) for project context)
> **Last Updated**: 2026-07-06

Spring Boot 3.2.5 API (port 9001), Java 17, MyBatis-Plus 3.5.16, MapStruct 1.6.3.

## STRUCTURE

```
src/main/java/com/ulticode/
├── UlticodeBackendApplication.java   # @SpringBootApplication + @EnableScheduling + @EnableAsync
├── common/
│   ├── annotation/                    # @Audited, @CheckBan, @RateLimit
│   ├── aspect/                        # AOP implementations for annotations above
│   ├── audit/                         # AuditPolicy — single source of truth for @Audited sites
│   ├── config/                        # 24 beans: SecurityConfig, RedisConfig, MybatisPlusConfig…
│   ├── dto/                           # Shared DTOs (PageResult, Result<T>)
│   ├── exception/                     # BusinessException, ErrorCode enum
│   ├── response/                      # Result<T> wrapper: {code, message, data, traceId}
│   ├── service/                       # Cross-cutting services
│   ├── util/                          # Utility classes
│   └── validation/                    # Custom validators
└── modules/                           # 26 domain modules (see table below)
    └── <module>/
        ├── controller/                # REST endpoints, @Valid input binding
        ├── service/                   # Interface + impl/ subdirectory
        ├── mapper/                    # MyBatis-Plus @Select/@Update (no XML)
        ├── entity/                    # DO classes (DB table mapping)
        ├── dto/                       # XxxDTO (input), XxxVO (output), XxxQuery (params)
        ├── projection/                # Read-side deep modules (entity→VO + cross-mapper enrichment)
        └── port/                      # Consumer-owned interfaces (dependency inversion)
```

### Module Map (26 modules)

| Module | Domain |
|--------|--------|
| `auth` | Login, register, OAuth, password reset, JWT issue/refresh |
| `user` | Profile, real-name, user stats |
| `problem` | Problems, test cases, SPJ, versions |
| `problemlist` | Problem lists / favorites |
| `submission` | Submit, judge dispatch, Replay |
| `solution` | Editorials, comments, votes |
| `contest` | Contests, standings, registration, virtual replay |
| `forum` | Posts, comments, boards |
| `vote` | Like/dislike (owns denormalized counters) |
| `notification` | In-app, email, push |
| `achievement` | Badges, streaks, leaderboards |
| `subscription` | Membership, payment, orders |
| `moderation` | Review queue, reports, appeals |
| `search` | Full-text / vector search (SearchReadProjection) |
| `i18n` | Translation key hosting |
| `bookmark` · `follow` | Bookmarks / follows |
| `email` | Mail templates, queue |
| `admin` · `permission` | Roles, permissions, audit logs |
| `monitoring` · `backup` | Monitoring / backup |
| `edgeoperations` · `queue` | Edge tasks, async queue (Outbox + Fencing) |
| `websocket` · `refreshtoken` | WS auth / token rotation |

## WHERE TO LOOK

| Task | Location |
|------|----------|
| Add REST endpoint | `modules/<mod>/controller/` → new `@RestController` method |
| Add service logic | `modules/<mod>/service/impl/` |
| Add DB query | `modules/<mod>/mapper/` — `@Select`/`@Update` annotations only (no XML) |
| Security config | `common/config/SecurityConfig.java` — JWT filter chain, PUBLIC_ENDPOINTS |
| Sandbox wiring | `modules/submission/config/DockerSandboxConfig.java` |
| WebSocket config | `modules/websocket/config/WebSocketConfig.java` |
| Add audit annotation | Update `common/audit/AuditPolicy` catalog + `AuditPolicyCoverageTest` |
| Add projection | `modules/<mod>/projection/` (mirror `ModerationProjection` pattern) |
| Add cross-module port | Consumer module defines interface in `port/`, provider implements adapter |

## Deep Modules

- **`common.audit.AuditPolicy`** — single catalog of every `@Audited` / `@CheckBan` site.
  `AuditPolicyCoverageTest` scans classpath and fails CI if catalog drifts from annotations.
- **`modules.<x>.projection.<X>Projection`** — read-side deep modules owning entity→VO shaping.
  Existing: `ModerationProjection`, `AchievementProjection`, `ProblemListProjection`.
  ADR-0011 lists scheduled additions.
- **Port/Adapter seams** — consumer-owned interfaces in `port/`, inverted from the provider:
  `ContestSubmissionPort`, `SubmissionAnalyticsPort`, `AdminSubmissionReadPort`,
  `AdminUserStatsReadPort`, `AdminCommentReadPort`, `AuthSessionPort`, `ProblemDetailPort`.
- **Realtime push seam** — six consumer-owned ports (`NotificationPushPort`, `BadgePushPort`,
  `SubmissionResultPushPort`, `ContestRankingMarkDirtyPort`, `ContestStatusPushPort`,
  `ContestAnnouncementPushPort`). Adapters in `websocket/port/adapter/`. See ADR-0009.

## CONVENTIONS

### Response Pattern
```java
Result.success(data)                          // {code:0, message:"success", data, traceId}
throw new BusinessException(ErrorCode.XXX)    // error response
PageResult.of(list, total, page, limit)       // pagination
```

### Entity Conventions
- Primary key: `String` (UUID), required fields: `id`, `create_time`, `update_time`
- Logical delete: DB `is_deleted` → entity field `deleted`
- Boolean: DB `is_xxx` → entity field `xxx`
- `create_time` / `update_time`: use `@TableField(insertStrategy = NEVER, updateStrategy = NEVER)`

### JaCoCo
- Thresholds: LINE ≥ 0.05, BRANCH ≥ 0.02 (intentional floor, not target)
- Excludes: `*Mapper`, `entity/`, `*DTO`, `*VO`, `*BO`, `*Response`, `*Request`, `*Config`, `*Properties`, `*Application`
- Runs on `verify` phase: `prepare-agent → test → report → check`

### Surefire
- `-Djdk.attach.allowAttachSelf=true` (Mockito 5 / ByteBuddy on Oracle JDK 17.0.2)
- Excludes `*IT.java` by naming convention

### Cache Keys
Pattern: `ulticode:{module}:{entity}:{id}`

## ANTI-PATTERNS

- **No XML Mappers** — use `@Select`/`@Update` annotations only
- **No `Map.of()` with nullable values** — throws NPE; use `HashMap` or `Objects.requireNonNullElse()`
- **Never throw from push ports** on missing/disconnected session — use no-op
- **Never re-query `EdgeOperationMapper.countByTargetAndOperation`** from non-vote services for counts — use `voteService.vote()` return values
- **Never silently fall back to `problem_examples`** in judge worker — fail closed (see `JudgeWorkerFailClosedIT`)
- **Push port impls MUST NOT throw** on missing subscription — silent no-op only
- **`@RequireArgsConstructor` + `@InjectMocks`**: every constructor param needs a `@Mock` field or silent NPE
- **WebSocket auth**: `access_token` cookie only — never query token, URL token, or STOMP header token
- **Audit identity**: from authenticated principal only — never from request body

## COMMANDS

```bash
./mvnw compile -B                  # compile
./mvnw test -B                     # unit tests (excludes *IT.java)
./mvnw -Dtest='*IT' test -B        # integration tests (Testcontainers)
./mvnw verify -B                   # compile + unit + JaCoCo check
./mvnw package -DskipTests         # JAR only (finalName: app)
./mvnw spring-boot:run             # direct run (skip tests)
```

## NOTES

- **No Spring profiles in `pom.xml`** — single build topology. Use `-Dspring.profiles.active=ci` at runtime.
- **CI feature-flag matrix**: `backend-test-features` runs `[off, on]` variants with 5 cutover flags.
- **Test count**: 100 `*Test.java` + 15 `*IT.java` files.
- **Coverage gaps**: `backup/`, `follow/`, `permission/`, `search/`, `subscription/` have minimal tests.
- **`@ConfigurationPropertiesScan`** on main class — `@ConfigurationProperties` records auto-registered.
- **MapStruct** generates DTO↔Entity mappers at compile time (no manual conversion code).
