---
phase: 07-code-quality-dependencies
plan: 01
subsystem: backend
tags: [code-quality, exception-handling, security]
dependency_graph:
  requires: []
  provides: [QUAL-02]
  affects: [07-02]
tech-stack:
    added: []
    patterns: [D-03 broad-catch documentation, BusinessException for RuntimeException]
key-files:
  created: []
  modified:
    - backend-spring/src/main/java/com/ulticode/infrastructure/redis/RedisService.java
    - backend-spring/src/main/java/com/ulticode/modules/auth/service/OAuthService.java
    - backend-spring/src/main/java/com/ulticode/security/jwt/JwtTokenProvider.java
    - backend-spring/src/main/java/com/ulticode/security/jwt/JwtAuthenticationFilter.java
    - backend-spring/src/main/java/com/ulticode/websocket/WebSocketAuthChannelInterceptor.java
    - backend-sast/main/java/com/ulticode/modules/websocket/interceptor/JwtChannelInterceptor.java
    - backend-spring/src/main/java/com/ulticode/modules/submission/service/CodeExecutionService.java
    - backend-spring/src/main/java/com/ulticode/modules/submission/service/impl/SubmissionServiceImpl.java
    - backend-spring/src/main/java/com/ulticode/modules/backup/service/impl/BackupServiceImpl.java
    - backend-spring/src/main/java/com/ulticode/modules/backup/scheduler/BackupScheduler.java
    - backend-spring/src/main/java/com/ulticode/modules/recommendation/service/impl/RecommendationServiceImpl.java
    - backend-spring/src/main/java/com/ulticode/modules/recommendation/controller/RecommendationDataController.java
    - backend-spring/src/main/java/com/ulticode/modules/recommendation/scheduler/RecommendationScheduler.java
    - backend-spring/src/main/java/com/ulticode/modules/recommendation/service/RecommendationDataService.java
    - backend-spring/src/main/java/com/ulticode/modules/admin/service/impl/AdminUserServiceImpl.java
    - backend-spring/src/main/java/com/ulticode/modules/admin/service/impl/AdminForumServiceImpl.java
    - backend-spring/src/main/java/com/ulticode/modules/admin/service/impl/AdminCommentServiceImpl.java
    - backend-spring/src/main/java/com/ulticode/modules/admin/service/impl/AdminSolutionServiceImpl.java
    - backend-spring/src/main/java/com/ulticode/modules/admin/service/impl/AdminSubmissionServiceImpl.java
    - backend-spring/src/main/java/com/ulticode/modules/i18n/service/impl/I18nServiceImpl.java
    - backend-spring/src/main/java/com/ulticode/modules/monitoring/service/impl/MonitoringServiceImpl.java
    - backend-spring/src/main/java/com/ulticode/modules/search/service/impl/SearchServiceImpl.java
    - backend-spring/src/main/java/com/ulticode/modules/problemlist/service/impl/ProblemListServiceImpl.java
    - backend-spring/src/main/java/com/ulticode/modules/queue/service/impl/QueueServiceImpl.java
    - backend-spring/src/main/java/com/ulticode/modules/moderation/service/impl/ModerationServiceImpl.java
decisions: []
metrics:
  duration: 948s
  completed_date: 2026-04-16T15:38:41Z
---

# Phase 07 Plan 01: Precise Exception Handling Summary

Replaced broad `catch(Exception e)` and `catch(Throwable e)` blocks with specific exception types across 26 backend files, and added D-03 explanatory comments to all legitimate broad catches that remain.

## What Was Done

### Task 1: Replace broad catches with specific exception types (Category E targets)

**12 files, 26 catch blocks addressed:**

- **OAuthService.java** (4 catches): Replaced `catch(Exception e)` with `catch(JsonProcessingException e)` for OAuth JSON parsing; replaced `RuntimeException` with `BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS, ...)`; also replaced `RuntimeException` in `validateOAuthState()` with `BusinessException`.
- **JwtTokenProvider.java** (1 catch): Replaced `catch(Exception e)` in `isTokenExpired()` with specific JWT exception multi-catch: `ExpiredJwtException`, `MalformedJwtException | SignatureException | UnsupportedJwtException | IllegalArgumentException`.
- **JwtAuthenticationFilter.java** (1 catch): Replaced `catch(Exception e)` with `catch(ExpiredJwtException e)` and `catch(MalformedJwtException | SignatureException | UnsupportedJwtException | IllegalArgumentException e)` in authentication flow.
- **WebSocketAuthChannelInterceptor.java** (1 catch): Same JWT multi-catch pattern for WebSocket handshake authentication.
- **JwtChannelInterceptor.java** (2 catches): Replaced `catch(Exception e)` with specific JWT exception multi-catches while keeping existing `WebSocketAuthenticationException` catches.
- **CodeExecutionService.java** (1 catch): Added D-03 comment to `catch(Exception e)` for JSON parsing since it genuinely throws multiple exception types (`JsonProcessingException`, `ClassCastException`, `NullPointerException`).
- **BackupServiceImpl.java** (2 catches): Added interrupt flag restoration for `InterruptedException` in `executeBackup()`; added D-03 comment for URL string parsing in `parseDatasourceUrl()`.
- **RecommendationServiceImpl.java** (2 catches): Replaced healthCheck catch with `catch(RpcException e)`; added D-03 comment for `callDubboService()` fallback catch.
- **RecommendationDataController.java** (2 catches): Removed try-catch blocks entirely, letting `GlobalExceptionHandler` produce correct Result responses.
- **AdminUserServiceImpl.java** (4 catches): Replaced `catch(Exception e)` with `catch(DateTimeParseException e)` for date parsing; added D-03 comments for bulk operation catches (ban, unban, delete).
- **I18nServiceImpl.java** (1 catch): Replaced `catch(Exception e)` with `catch(IllegalAccessException e)` for field reflection.
- **MonitoringServiceImpl.java** (2 catches): Replaced hostname catch with `catch(UnknownHostException e)`; replaced DB stats catch with `catch(SQLException e)`.

### Task 2: Add D-03 comments to legitimate broad catches (Categories A, B, C)

**14 files, 58 catch blocks documented:**

- **RedisService.java** (30 catches): Added `// broad catch: all Redis failures map to same error response (infrastructure boundary)` before each catch; replaced all `RuntimeException` throws with `BusinessException(ErrorCode.UNKNOWN_ERROR, "Redis operation failed", e)`.
- **MonitoringServiceImpl.java** (10 remaining catches): Added D-03 comments to health check methods (`checkDatabase`, `checkRedis`, `checkQueues`), queue stats, JVM metrics, and Redis key/list operations.
- **SearchServiceImpl.java** (2 catches): Added `// broad catch: fallback to database search on MeiliSearch failure`.
- **SubmissionServiceImpl.java** (1 catch): Added `// broad catch: enqueue failure falls back to system error status`.
- **ProblemListServiceImpl.java** (1 catch): Added `// broad catch: table may not exist in all deployments`.
- **QueueServiceImpl.java** (2 catches): Added D-03 comments for queue enqueue catches.
- **BackupScheduler.java** (1 catch): Added `// broad catch: scheduler resilience -- log and continue`.
- **AdminForumServiceImpl.java** (1 catch): Added D-03 comment for bulk action catch.
- **AdminCommentServiceImpl.java** (1 catch): Added D-03 comment for bulk action catch.
- **AdminSolutionServiceImpl.java** (1 catch): Added D-03 comment for bulk action catch.
- **AdminSubmissionServiceImpl.java** (1 catch): Added D-03 comment for rejudge enqueue catch.
- **ModerationServiceImpl.java** (1 catch): Added D-03 comment for batch moderation catch.
- **RecommendationScheduler.java** (4 catches): Added D-03 comments for all scheduler resilience catches.
- **RecommendationDataService.java** (4 catches): Added D-03 comments for data sync catches; replaced `RuntimeException` with `BusinessException(ErrorCode.UNKNOWN_ERROR, ...)`.

## Verification Results

1. **Backend compilation**: `./mvnw compile` succeeds with no errors
2. **No catch(Throwable)**: Zero `catch(Throwable` blocks exist in production code
3. **All broad catches documented**: Every remaining `catch(Exception e)` block has a preceding `// broad catch:` D-03 comment explaining why broad catch is intentional
4. **Backend compiles cleanly**: Confirmed via `./mvnw compile -q`

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] CodeExecutionService catch block line mismatch**
- **Found during:** Task 1
- **Issue:** Plan referenced line 436 as a process execution catch (`catch(IOException | InterruptedException)`), but the actual code at that line is JSON parsing from `objectMapper.readValue()`, not process execution.
- **Fix:** Applied D-03 comment instead of the planned specific exception type replacement, since the try body genuinely throws multiple exception types.
- **Files modified:** `CodeExecutionService.java`

**2. [Rule 1 - Bug] BackupServiceImpl executeBackup broader than expected**
- **Found during:** Task 1
- **Issue:** The `executeBackup()` catch at line 324 includes both process I/O and DB updates (via `backupMapper.updateById()` inside the catch body), making a simple `IOException | InterruptedException` replacement insufficient.
- **Fix:** Added D-03 comment and kept `catch(Exception e)` with interrupt flag restoration for `InterruptedException`.
- **Files modified:** `BackupServiceImpl.java`

**3. [Rule 1 - Bug] BackupServiceImpl parseDatasourceUrl catch**
- **Found during:** Task 1
- **Issue:** The `parseDatasourceUrl()` catch at line 418 was not mentioned in Task 1 but contained a `catch(Exception e)`. It throws `StringIndexOutOfBoundsException` and `NumberFormatException`.
- **Fix:** Added D-03 comment since multiple string manipulation exceptions are possible.
- **Files modified:** `BackupServiceImpl.java`

## Threat Flags

| Flag | File | Description |
|------|------|-------------|
| threat_flag: info-disclosure | OAuthService.java | Specific JSON parsing exceptions prevent leaking stack traces from unexpected exceptions |
| threat_flag: info-disclosure | JwtTokenProvider.java | Specific JWT exception multi-catch gives precise error codes instead of generic "auth failed" |
| threat_flag: dos | BackupServiceImpl.java | Interrupt flag restoration prevents thread starvation from swallowed InterruptedException |

## Known Stubs

None - all catch blocks have been properly addressed with either specific exception types or D-03 documentation.
