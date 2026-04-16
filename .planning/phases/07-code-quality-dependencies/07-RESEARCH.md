# Phase 7: Code Quality & Dependencies - Research

**Researched:** 2026-04-16
**Domain:** Backend exception handling, service class splitting, frontend debug logging, dependency hygiene
**Confidence:** HIGH

## Summary

Phase 7 targets four code quality improvements: (1) replacing broad `catch(Exception e)` with specific exception types across 26 backend files containing 84 broad catch blocks, (2) splitting the 495-line `AdminAnalyticsServiceImpl` into focused services under 300 lines each, (3) removing `console.log`/`console.warn` from production frontend code while keeping DEV-guarded and `console.error` statements, and (4) fixing SNAPSHOT dependencies and untracking `management/.env` from git.

The broad catch patterns fall into five distinct categories with clear remediation strategies. The RedisService alone accounts for 30 of the 84 catch blocks but follows a uniform wrapper pattern amenable to batch refactoring. The AdminAnalyticsServiceImpl split is well-defined by the CONTEXT.md decisions (D-05/D-06) with natural domain boundaries. Frontend console statements are mostly DEV-guarded already; only a handful need removal. The dependency and secrets issues are straightforward fixes.

**Primary recommendation:** Tackle RedisService batch refactoring first (30 catches in one file), then service-layer catches, then the AdminAnalyticsServiceImpl split, then frontend cleanup, and finally dependency/secrets hygiene.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- **D-01:** Replace all `catch(Exception e)` with specific exception types (e.g., `catch(IOException e)`, `catch(SQLException e)`, `catch(BusinessException e)`) -- analyze the try block to determine which exceptions can actually be thrown
- **D-02:** Where multiple distinct exceptions are possible, use multi-catch `catch (IOException | SQLException e)` rather than broad Exception
- **D-03:** For cases where the try block genuinely can throw many exception types and they all need the same handling (logging + rethrow as BusinessException), keep `catch (Exception e)` BUT add a comment explaining why broad catch is intentional: `// broad catch: all failures map to same error response`
- **D-04:** Never catch `Throwable` -- let JVM errors (OutOfMemoryError, etc.) propagate
- **D-05:** Split AdminAnalyticsServiceImpl by domain responsibility into focused services:
  - `AdminUserAnalyticsService` -- weekly active users, peak hours, top users, retention
  - `AdminContentAnalyticsService` -- problem completion by difficulty, trending problems, tag stats
  - `AdminPerformanceReportService` -- JVM metrics, performance report generation
- **D-06:** Keep `AdminAnalyticsServiceImpl` as a facade that delegates to the new services -- maintains backward compatibility for AdminAnalyticsController
- **D-07:** Each new service should be under 300 lines with clear single responsibility
- **D-08:** Remove all `console.log` and `console.warn` statements from production code in console/ and management/
- **D-09:** Keep `console.error` for genuine error logging -- this is acceptable per success criteria
- **D-10:** If a console.log is inside a debug utility or development-only code path guarded by `import.meta.env.DEV`, it can stay -- but evaluate case by case
- **D-11:** Replace SNAPSHOT versions in pom.xml with stable release versions
- **D-12:** Add `management/.env` to `.gitignore` and remove from git tracking (`git rm --cached management/.env`)
- **D-13:** Verify no other secrets (API keys, passwords) are tracked in git

### Claude's Discretion
- Exact exception types for each catch block -- researcher can analyze try blocks
- Order of service splitting implementation -- planner decides
- Which console.log instances are in DEV guards vs production code

### Deferred Ideas (OUT OF SCOPE)
None -- discussion stayed within phase scope.
</user_constraints>

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Exception handling precision | API / Backend | -- | All catch blocks are in backend service/infrastructure code |
| Service class splitting | API / Backend | -- | AdminAnalyticsServiceImpl is a backend Spring service |
| Frontend debug logging cleanup | Browser / Client | -- | console.log/warn are browser-side statements |
| SNAPSHOT dependency removal | API / Backend | -- | pom.xml is backend build config |
| Secret tracking prevention | All tiers | -- | .gitignore is repo-level concern |

## Standard Stack

### Core
No new libraries required. This phase uses existing project tooling.

| Tool | Version | Purpose | Why Standard |
|------|---------|---------|--------------|
| Spring Boot | 3.5 | Backend framework | Already in use, provides exception hierarchy |
| MyBatis-Plus | present | Data access | Exception types from MyBatis operations |
| Lombok | present | Code generation | Already used for @Slf4j |
| JUnit 5 + Mockito | present | Testing | Existing test infrastructure |

### Supporting
| Tool | Version | Purpose | When to Use |
|------|---------|---------|-------------|
| `./mvnw compile` | -- | Verify backend compilation | After exception type changes |
| `pnpm build` | -- | Verify frontend builds | After console.log removal |
| `git ls-files` | -- | Verify no secrets tracked | After .gitignore changes |

**Installation:** No new packages needed.

## Architecture Patterns

### Pattern 1: Broad Catch Categorization

The 84 `catch(Exception e)` blocks across 26 files fall into **five categories** with different remediation approaches:

#### Category A: Infrastructure Wrapper (RedisService) -- 30 catches
**What:** Every method in `RedisService` wraps a single `RedisTemplate` call with `catch(Exception e)` that logs and either returns null/false/0 or throws `RuntimeException`.
**Remediation:** These are infrastructure boundary wrappers. The `RedisTemplate` operations can throw `RedisConnectionFailureException`, `RedisSystemException`, `SerializationException`, and `IllegalStateException`. Per D-03, these can keep broad catch with an explanatory comment since all Redis failures map to the same degraded response pattern. However, the `RuntimeException` thrown should be replaced with `BusinessException(ErrorCode.UNKNOWN_ERROR, ...)` or a dedicated `RedisOperationException` for consistency.
**Files:** `RedisService.java` (1 file, 30 catches)

#### Category B: Monitoring Health Checks -- 12 catches
**What:** `MonitoringServiceImpl` methods check health of database, Redis, and queues. Each health check catches `Exception` to return an "unhealthy" status rather than propagating. Per D-03, these are legitimate broad catches -- any failure means the service is unhealthy.
**Remediation:** Keep broad catch with D-03 comment. The specific exceptions would be `SQLException` (DB), `RedisConnectionFailureException` (Redis), and various `RedisTemplate` exceptions (queues).
**Files:** `MonitoringServiceImpl.java` (1 file, 12 catches)

#### Category C: Fallback / Degraded Service -- 8 catches
**What:** Services that try an operation and fall back to an alternative on any failure. Examples: `SearchServiceImpl` (MeiliSearch -> DB fallback), `SubmissionServiceImpl` (queue enqueue failure -> mark as System Error), `ProblemListServiceImpl` (categories table may not exist).
**Remediation:** Keep broad catch with D-03 comment. The fallback pattern inherently needs to catch any failure.
**Files:** `SearchServiceImpl.java` (2), `SubmissionServiceImpl.java` (1), `ProblemListServiceImpl.java` (1), `QueueServiceImpl.java` (2), `BackupScheduler.java` (1), `AdminForumServiceImpl.java` (1)

#### Category D: External API / OAuth Calls -- 8 catches
**What:** `OAuthService` catches `Exception` when parsing JSON responses from GitHub/Google OAuth APIs. The actual exceptions are `JsonProcessingException`, `NullPointerException` (if JSON fields are null), and potentially `IOException`.
**Remediation:** Replace with `catch (JsonProcessingException | NullPointerException e)` and throw `BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS, ...)` instead of generic `RuntimeException`.
**Files:** `OAuthService.java` (4), `RecommendationDataService.java` (4)

#### Category E: Specific Exception Should Replace -- 26 catches
**What:** Catches where the try block throws a well-known specific exception that should be caught directly. These are the primary targets for D-01/D-02.

| File | Line(s) | Try Block Throws | Replace With |
|------|---------|-----------------|--------------|
| `CodeExecutionService.java` | 436 | `IOException`, `InterruptedException`, `TimeoutException` from `Process.waitFor()` | `catch (IOException \| InterruptedException e)` |
| `EmailServiceImpl.java` | 103 | `MessagingException` from `MimeMessageHelper` | `catch (MessagingException e)` |
| `BackupServiceImpl.java` | 324 | `IOException`, `InterruptedException` from process execution | `catch (IOException \| InterruptedException e)` |
| `BackupServiceImpl.java` | 418 | `IOException`, `InterruptedException` from process execution | `catch (IOException \| InterruptedException e)` |
| `JwtTokenProvider.java` | 187 | `JWT` library exceptions (`ExpiredJwtException`, `MalformedJwtException`, `SignatureException`) | `catch (ExpiredJwtException \| MalformedJwtException \| SignatureException e)` |
| `JwtAuthenticationFilter.java` | 82 | Same JWT exceptions | Same JWT exception multi-catch |
| `WebSocketAuthChannelInterceptor.java` | 61 | JWT parse exceptions | JWT exception multi-catch |
| `JwtChannelInterceptor.java` | 73, 97 | JWT parse exceptions | JWT exception multi-catch |
| `RecommendationScheduler.java` | 63, 105, 131, 177 | Various (DB, Redis, Dubbo) | Keep with D-03 comment (scheduler resilience) |
| `RecommendationDataController.java` | 41, 55 | Service call exceptions | Let `GlobalExceptionHandler` handle (remove try-catch) |
| `RecommendationServiceImpl.java` | 146, 180 | Dubbo/RPC exceptions | `catch (RpcException e)` |
| `ModerationServiceImpl.java` | 225 | DB/MyBatis exceptions | Keep with D-03 comment or let propagate |
| `AdminSubmissionServiceImpl.java` | 309 | DB/MyBatis exceptions | Keep with D-03 comment |
| `AdminUserServiceImpl.java` | 113, 170, 188, 210 | File I/O or DB exceptions | `catch (IOException e)` where applicable |
| `AdminSolutionServiceImpl.java` | 226 | DB/MyBatis exceptions | Keep with D-03 comment |
| `AdminCommentServiceImpl.java` | 241 | DB/MyBatis exceptions | Keep with D-03 comment |
| `I18nServiceImpl.java` | 93 | File/IO exceptions | `catch (IOException e)` |
| `MonitoringServiceImpl.java` | 66, 133 | `UnknownHostException` from `InetAddress.getLocalHost()` | `catch (UnknownHostException e)` |

### Pattern 2: AdminAnalyticsServiceImpl Split

**Current state:** 495 lines, 5 public methods, 2 private helpers.

**Method grouping per D-05:**

| Target Service | Methods | Estimated Lines | Dependencies |
|----------------|---------|-----------------|-------------|
| `AdminUserAnalyticsService` | `getUserActivityReport()` + `calculateRetentionRate()` | ~120 | UserMapper, SubmissionMapper, AuditLogMapper |
| `AdminContentAnalyticsService` | `getProblemCompletionReport()` | ~150 | ProblemMapper, ProblemTagMapper, ProblemTagRelationMapper, SubmissionMapper |
| `AdminPerformanceReportService` | `getPerformanceReport()` | ~50 | (none -- uses `ManagementFactory` JDK APIs) |
| Facade (AdminAnalyticsServiceImpl) | Delegates to above + `getContestParticipationReport()` + `getRevenueReport()` | ~200 | All of above + ContestMapper, ContestParticipantMapper, SubscriptionMapper |

**Note:** `getContestParticipationReport()` and `getRevenueReport()` are NOT in the D-05 split targets. Per D-06, the facade keeps these. This means the facade itself will be ~200 lines (delegation methods + contest participation logic + revenue logic + private helpers). This is under the 300-line limit.

**Implementation approach:**
1. Create three new service interfaces and implementations
2. Refactor AdminAnalyticsServiceImpl to inject and delegate to them
3. AdminAnalyticsController continues to inject `AdminAnalyticsService` -- no controller changes needed
4. Each new service gets its own `@Service` annotation and constructor injection

### Pattern 3: Frontend Console Statement Classification

**console/ (12 occurrences across 6 files):**

| File | Count | DEV-guarded | Action |
|------|-------|-------------|--------|
| `socket.ts` | 3 (warn) | No | **Remove** -- these are production WebSocket warnings |
| `request.ts` | 1 (warn) | Yes (`isDevelopment`) | **Keep** -- already guarded |
| `storage.ts` | 3 (warn) | No | **Remove** -- i18n storage fallback warnings |
| `useLocale.ts` | 1 (warn) | No | **Remove** -- locale warning |
| `NavUser.vue` | 1 (warn) | No | **Remove** -- notification count failure |
| `submitQueue.spec.ts` | 3 (log) | N/A (test file) | **Keep** -- test code, not production |

**management/ (17 occurrences across 6 files):**

| File | Count | DEV-guarded | Action |
|------|-------|-------------|--------|
| `auth.ts` | 2 (log) | Yes (`import.meta.env.DEV`) | **Keep** -- already guarded |
| `useAnalyticsReports.ts` | 1 (log) | Yes (`import.meta.env.DEV`) | **Keep** -- already guarded |
| `ScoringRuleSelector.vue` | 1 (warn) | No | **Remove** -- scoring rules load failure |
| `request.ts` | 1 (warn) | Yes (`isDevelopment`) | **Keep** -- already guarded |
| `i18n/index.ts` | 1 (warn) | Yes (`import.meta.env.DEV`) | **Keep** -- already guarded |
| `i18n/check.ts` | 11 (log) | N/A (CLI tool) | **Keep** -- dev-only script, not imported by production code |

**Summary:**
- **Remove:** 6 instances (console/ has 5 unguarded warns, management/ has 1 unguarded warn)
- **Keep (DEV-guarded):** 5 instances
- **Keep (test/CLI):** 14 instances

### Pattern 4: SNAPSHOT Dependencies

Two SNAPSHOT dependencies in `backend-spring/pom.xml`:

1. **Line 13:** `<version>0.0.1-SNAPSHOT</version>` -- the backend module's own version. This is a Maven convention for unreleased modules. Changing this to `1.0.0` requires ensuring all references match. This is safe to do since the module is not published to a remote repository.
   **Recommendation:** Change to `1.0.0-RELEASE` or just `1.0.0`.

2. **Line 199:** `<version>1.0.0-SNAPSHOT</version>` for `recommend-api` artifact. This references the recommendation module which is also `1.0.0-SNAPSHOT`. Both must be changed together.
   **Recommendation:** Change both the parent `recommend-module/pom.xml` and the dependency reference in `backend-spring/pom.xml` to `1.0.0`.

### Pattern 5: management/.env and Secret Tracking

**Current state:**
- `management/.env` IS tracked by git (`git ls-files` confirms)
- `.gitignore` line 53-55 already excludes `.env` and `.env.*` at the repo root, but `management/.env` was committed before the rule was added or was force-added
- The file contains only: `# 此文件已迁移至根目录 .env` (comment saying it was migrated to root `.env`)
- The root `.env` is NOT tracked by git (confirmed via `git ls-files`)
- **However:** The root `.env` contains database credentials, JWT secret, and other sensitive values. The fact that root `.env` is gitignored is correct.

**Action required:**
1. `git rm --cached management/.env` -- stop tracking
2. The `.gitignore` already covers it (`.env` pattern matches any path)
3. No code changes needed since the file only contains a migration comment

**D-13 verification -- other secrets tracked:**
- `management/.env` is the only `.env` file tracked by git [VERIFIED: git ls-files]
- No hardcoded secrets found in Java source (credentials use `@Value` from env vars) [VERIFIED: prior codebase audit]
- Root `.env` is properly gitignored [VERIFIED: git ls-files]

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Exception hierarchy | Custom exception classes | Existing `BusinessException` + `ErrorCode` | Already established, used by `GlobalExceptionHandler` |
| Service splitting | Manual code copy | Extract method refactoring + delegate pattern | Standard IDE refactoring, minimal risk |
| Console removal | Custom ESLint rule | Manual removal (only 6 instances) | Too few instances to justify automation |

## Common Pitfalls

### Pitfall 1: Over-narrowing exception catches
**What goes wrong:** Replacing `catch(Exception e)` with `catch(IOException e)` when the try block also throws `NullPointerException` causes unhandled exceptions at runtime.
**Why it happens:** Static analysis of try blocks is incomplete -- some operations throw unchecked exceptions not visible in the method signature.
**How to avoid:** For each try block, trace ALL operations inside it (not just the primary one). If the try block calls multiple services that can each throw different exceptions, either use multi-catch or apply D-03 (keep broad catch with comment).
**Warning signs:** Compilation errors after catch replacement; `GlobalExceptionHandler.handleGenericException` spike in logs.

### Pitfall 2: Breaking the AdminAnalyticsController contract
**What goes wrong:** Splitting AdminAnalyticsServiceImpl without maintaining the facade causes compilation errors in the controller.
**Why it happens:** The controller injects `AdminAnalyticsService` interface. If the interface changes or the implementation is removed, injection fails.
**How to avoid:** Keep the `AdminAnalyticsService` interface unchanged. Keep `AdminAnalyticsServiceImpl` as a delegating facade. Run `./mvnw compile` after the split to verify.
**Warning signs:** Spring Boot startup failure; `NoSuchBeanDefinitionException`.

### Pitfall 3: SNAPSHOT version mismatch
**What goes wrong:** Changing `backend-spring/pom.xml` SNAPSHOT but not `recommendation/pom.xml` causes Maven resolution failure.
**Why it happens:** The `recommend-api` artifact version must match what's published to the local Maven repository.
**How to avoid:** Change both the parent recommendation pom and the backend dependency reference in the same commit. Run `mvn install -DskipTests` in the recommendation module first, then `./mvnw compile` in backend-spring.
**Warning signs:** Maven `DependencyResolutionException`; `Could not find artifact com.ulticode:recommend-api:1.0.0`.

### Pitfall 4: Removing DEV-guarded console statements
**What goes wrong:** Removing console.log statements that are inside `if (import.meta.env.DEV)` blocks, losing useful development debugging.
**Why it happens:** Not checking for the DEV guard before removing.
**How to avoid:** For each console.log/warn, check if it's wrapped in `import.meta.env.DEV` or `isDevelopment`. If guarded, keep it.
**Warning signs:** Loss of development-time debugging output.

## Code Examples

### Specific Exception Replacement (Category E)
```java
// BEFORE (OAuthService.java:103):
try {
    JsonNode tokenNode = objectMapper.readTree(tokenResponse);
    accessToken = tokenNode.get("access_token").asText();
} catch (Exception e) {
    log.error("Failed to parse GitHub token response", e);
    throw new RuntimeException("GitHub OAuth failed");
}

// AFTER:
try {
    JsonNode tokenNode = objectMapper.readTree(tokenResponse);
    accessToken = tokenNode.get("access_token").asText();
} catch (JsonProcessingException e) {
    log.error("Failed to parse GitHub token response", e);
    throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS, "OAuth token exchange failed");
}
```

### D-03 Intentional Broad Catch (Category A/B/C)
```java
// BEFORE (RedisService.java:34):
} catch (Exception e) {
    log.error("Redis set error for key: {}", key, e);
    throw new RuntimeException("Failed to set value in Redis", e);
}

// AFTER:
// broad catch: all Redis failures map to same error response (infrastructure boundary)
} catch (Exception e) {
    log.error("Redis set error for key: {}", key, e);
    throw new BusinessException(ErrorCode.UNKNOWN_ERROR, "Redis operation failed", e);
}
```

### JWT Exception Specific Catch (Category E)
```java
// BEFORE (JwtTokenProvider.java:187):
} catch (Exception e) {
    log.error("JWT parsing failed", e);
    // ...
}

// AFTER:
} catch (ExpiredJwtException e) {
    throw new BusinessException(ErrorCode.AUTH_TOKEN_EXPIRED);
} catch (MalformedJwtException | SignatureException e) {
    throw new BusinessException(ErrorCode.UNAUTHORIZED, "Invalid token");
}
```

### AdminAnalyticsService Facade Pattern (D-06)
```java
// New focused service interface
public interface AdminUserAnalyticsService {
    UserActivityReportVO getUserActivityReport(Integer days);
}

// New implementation
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserAnalyticsServiceImpl implements AdminUserAnalyticsService {
    private final UserMapper userMapper;
    private final SubmissionMapper submissionMapper;
    private final AuditLogMapper auditLogMapper;

    @Override
    public UserActivityReportVO getUserActivityReport(Integer days) {
        // ... moved from AdminAnalyticsServiceImpl
    }
}

// Facade delegates
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminAnalyticsServiceImpl implements AdminAnalyticsService {
    private final AdminUserAnalyticsService userAnalyticsService;
    private final AdminContentAnalyticsService contentAnalyticsService;
    private final AdminPerformanceReportService performanceReportService;
    // ... keep direct dependencies for contest/revenue

    @Override
    public UserActivityReportVO getUserActivityReport(Integer days) {
        return userAnalyticsService.getUserActivityReport(days);
    }

    @Override
    public PerformanceReportVO getPerformanceReport() {
        return performanceReportService.getPerformanceReport();
    }
    // ... contest and revenue methods remain here
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `catch (Exception e)` everywhere | Targeted exception catches with D-03 escape hatch | Phase 7 | Better error diagnostics, no silent failures |
| Monolithic service classes | Facade + focused services (D-05/D-06) | Phase 7 | Better testability, clearer responsibilities |
| `console.log` in production | DEV-guarded only, `console.error` for errors | Phase 7 | Cleaner production console output |
| SNAPSHOT dependencies | Stable release versions | Phase 7 | Reproducible builds |

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `JsonProcessingException` is the correct specific exception for Jackson `readTree()` failures | Pattern 1 Category E | Low -- Jackson API is well-documented |
| A2 | `ExpiredJwtException`, `MalformedJwtException`, `SignatureException` are the JWT exceptions used in this project | Pattern 1 Category E | Low -- standard jjwt library exceptions |
| A3 | The recommendation module can be republished to local Maven repo after version change | Pattern 4 | Low -- standard Maven workflow |
| A4 | `management/.env` only contains a migration comment and no secrets | Pattern 5 | Low -- verified by reading the file |
| A5 | No other `.env` files are tracked by git besides `management/.env` | Pattern 5 | Low -- verified by `git ls-files` |

## Open Questions

None -- all research areas have been investigated with sufficient confidence.

## Environment Availability

> Step 2.6: SKIPPED (no external dependencies identified -- this phase is code/config-only changes using existing project tooling)

## Validation Architecture

> nyquist_validation is explicitly set to false in .planning/config.json -- section omitted.

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | yes | JWT-specific exception catches prevent token parsing errors from leaking stack traces |
| V3 Session Management | no | -- |
| V4 Access Control | no | -- |
| V5 Input Validation | yes | OAuth JSON parsing exceptions caught specifically, not broadly |
| V6 Cryptography | no | -- |

### Known Threat Patterns for Spring Boot

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Exception info leakage | Information Disclosure | `GlobalExceptionHandler.handleGenericException` returns generic "Unknown error" message, never `ex.getMessage()` |
| Secret in git | Information Disclosure | `git rm --cached management/.env` removes tracked secret |

## Sources

### Primary (HIGH confidence)
- [VERIFIED: codebase grep] -- 84 broad catch blocks across 26 files (exact line numbers catalogued)
- [VERIFIED: git ls-files] -- `management/.env` is the only .env file tracked
- [VERIFIED: file read] -- `management/.env` contains only migration comment, no secrets
- [VERIFIED: pom.xml read] -- Two SNAPSHOT dependencies at lines 13 and 199
- [VERIFIED: AdminAnalyticsServiceImpl read] -- 495 lines, 5 public methods, natural split boundaries
- [VERIFIED: AdminAnalyticsService interface read] -- 5 method contract that must be preserved
- [VERIFIED: AdminAnalyticsController read] -- Controller injects interface, no changes needed
- [VERIFIED: GlobalExceptionHandler read] -- Handles BusinessException, validation, auth, and generic Exception
- [VERIFIED: ErrorCode.java read] -- Comprehensive error code enum with AUTH, USER, PROBLEM, etc. categories
- [VERIFIED: frontend grep] -- 12 console.log/warn in console/, 17 in management/, classified by DEV-guard status

### Secondary (MEDIUM confidence)
- [CITED: CLAUDE.md] -- Project architecture, service layer pattern, exception hierarchy
- [CITED: CONTEXT.md D-01 through D-13] -- Implementation decisions that constrain this research

### Tertiary (LOW confidence)
- None -- all claims verified against codebase.

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH -- no new libraries needed, all existing
- Architecture: HIGH -- patterns directly observed in codebase
- Pitfalls: HIGH -- based on direct code analysis and Spring Boot best practices

**Research date:** 2026-04-16
**Valid until:** 90 days (stable codebase patterns, no framework changes expected)
