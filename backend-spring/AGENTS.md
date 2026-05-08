# AGENTS.md — backend-spring

> **Last Updated**: 2026-05-07

## OVERVIEW

Spring Boot 3.2.5 (Java 17) REST API with 29 modules. Entry: `UlticodeBackendApplication.java` (port 9001).

## STRUCTURE

```
com.ulticode/
├── modules/              # auth, user, problem, submission, contest, forum,
│                          # solution, notification, subscription, moderation,
│                          # search, achievement, i18n, backup, email, monitoring,
│                          # vote, admin, bookmark, edgeoperations, permission,
│                          # problemlist, queue, recommendation, refreshtoken,
│                          # websocket, follow
├── common/               # annotation/, aspect/, config/, dto/, exception/, filter/, response/, service/, util/
├── security/             # JWT, CSRF filters & providers
├── infrastructure/       # Redis service layer
└── websocket/            # WebSocket config
```
Each module: `controller/` → `service/` → `entity/` → `mapper/` → `dto/`

## WHERE TO LOOK

| Need | Location |
|------|----------|
| Result wrapper | `common/response/Result.java` |
| Global exception handler | `common/exception/GlobalExceptionHandler.java` |
| Security config | `common/config/SecurityConfig.java` |
| Custom annotations | `common/annotation/` (@RequireRole, @RateLimit, @CheckBan, @CurrentUser) |
| JWT/CSRF | `security/jwt/JwtTokenProvider.java`, `security/csrf/CsrfService.java` |
| Redis service | `infrastructure/redis/RedisService.java` |
| Error codes | `common/constants/ErrorCode.java` |

## CONVENTIONS

- **Response**: `Result<T>` with `code:0` = success
- **Validation**: `@Valid` on DTOs; custom validators in `common/exception/`
- **Transactions**: `@Transactional(readOnly = true)` for reads; `@Transactional` for writes
- **Async**: `@Async` + `EnableAsync`; use `CompletableFuture` for parallel ops
- **Testing**: `@ExtendWith(MockitoExtension.class)`, `@Nested` + `@DisplayName`
- **IT tests**: `*IT.java` suffix with Testcontainers; CI excludes (`-Dtest='!*IT'`)
- **DTO naming**: `CreateXxxDTO`, `UpdateXxxDTO`, `XxxQueryDTO`, `XxxVO`
- **MyBatis-Plus**: `BaseMapper<T>` + wrapper queries; avoid raw SQL

## ANTI-PATTERNS

- ❌ `new` for services/controllers — use constructor injection
- ❌ Catch generic `Exception` — use `BusinessException`
- ❌ Return `null` for collections — return empty list/set
- ❌ `@Transactional` without `readOnly=true` on read methods
- ❌ `System.out.println` — use SLF4J logging
- ❌ Magic numbers — use `ErrorCode` constants or enums
