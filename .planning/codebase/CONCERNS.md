# Codebase Concerns Report

> Generated: 2026-04-30 (updated from 2026-04-29 analysis)
> Scope: Full codebase analysis

---

## 1. Security Risks

### Critical (P0)

| Severity | Issue | Location | Details |
|----------|-------|----------|---------|
| **HIGH** | Hardcoded JWT Secret | `backend-spring/start-backend.sh:4` | Contains hardcoded JWT secret `5GXMfun06YtfZSSV5h3M7yNA9fmuagbY5dITQyqSVDfcgebV-DqD9upy0zsSpPbKVKdRh4kllefbUFaTDuvpSA` |
| **HIGH** | Hardcoded JWT Secret | `backend-spring/ecosystem.config.cjs:9` | Same JWT secret in PM2 config |
| **HIGH** | Hardcoded Database Password | `backend-spring/start-backend.sh:3` | Database URL contains hardcoded password `2%5BOGT%23ds%3E1h7xZM%3CO%5D7%3B2%5BF%26` |
| **HIGH** | SQL Injection | `backend-spring/.../submission/mapper/SubmissionMapper.java:370-379` | Uses `${contestIds}` string interpolation instead of MyBatis `#{}` parameter binding. Vulnerable if `contestIds` is user-controlled. NOTE comment acknowledges this. |
| **HIGH** | CSRF Token Exposure | `backend-spring/.../auth/service/impl/AuthServiceImpl.java:280` | CSRF cookie set without `HttpOnly` flag — XSS can steal CSRF token |

### High Priority (P1)

| Severity | Issue | Location | Details |
|----------|-------|----------|---------|
| **MEDIUM** | Insecure Cookie Default | `backend-spring/.../application.yml:65,72` | `secure: ${JWT_COOKIE_SECURE:false}` defaults to `false` — cookies won't have Secure flag if not overridden |
| **MEDIUM** | Command Injection Risk | `backend-spring/.../submission/service/impl/SandboxServiceImpl.java:182-188` | User code embedded in shell commands via string concatenation. Docker sandbox provides isolation but risk exists if sandbox escapes |
| **MEDIUM** | OAuth State Reuse | `backend-spring/.../auth/service/OAuthService.java:66,146` | OAuth state stored in Redis without expiration validation |
| **MEDIUM** | Broad Exception Catching | 23+ files | `catch (Exception e)` masks errors and makes debugging difficult. High-risk files: `MonitoringServiceImpl.java` (11 catch blocks), `SubmissionServiceImpl.java` (4 catch blocks) |

### Low Priority / Mitigated (P2)

| Severity | Issue | Location | Details |
|----------|-------|----------|---------|
| LOW | Math.random() Usage | Multiple Vue files | Used for UI IDs only — not security tokens. Acceptable |
| LOW | XSS via v-html | Frontend Vue components | All `v-html` routed through DOMPurify — properly sanitized |
| LOW | JWT Implementation | `backend-spring/.../security/jwt/` | Uses jjwt with HMAC-SHA256, httpOnly cookies, token blacklist — well implemented |
| LOW | Authorization | Backend controllers | `@PreAuthorize` annotations used consistently |
| LOW | Password Storage | Throughout backend | Uses BCrypt — compliant |
| LOW | Security Headers | `backend-spring/.../common/config/SecurityConfig.java:113-136` | CSP, HSTS, XSS protection, frame-options properly configured |

---

## 2. Dependency Vulnerabilities

### Frontend (console & management)

| Package | Version | Severity | Issue | Status |
|---------|---------|----------|-------|--------|
| `markdown-it-katex` | 2.0.3 | **HIGH** | XSS vulnerability (GHSA-5ff8-jcf9-fw62) — no fix available | Unmaintained |
| `dompurify` | 3.3.3 | MODERATE | Multiple XSS/Prototype Pollution CVEs | Update available |
| `axios` | 1.14.0 | MODERATE | SSRF (GHSA-3p68-rc4w-qgx5), Header Injection (GHSA-fvcv-3m26-pcqx) | Update available |
| `sockjs-client` | 1.6.1 | MODERATE | Deprecated — no updates since 2023 | Migrate to native WebSocket |
| `dnd-kit-vue` | 0.0.2 | LOW | Abandoned — only 2 versions ever released | Use `@dnd-kit/core` directly |
| `vite-plugin-pwa` | 1.2.0 | LOW | Older version with transitive vulnerable dependencies | Update when convenient |

**Both frontends affected by:** `markdown-it-katex` (XSS, no fix), `dompurify` (moderate CVEs), `follow-redirects` (header leakage via axios)

### Backend (Spring Boot)

| Package | Version | Severity | Issue | Status |
|---------|---------|----------|-------|--------|
| `jjwt` | 0.13.0 | **HIGH** | CVE-2023-5062, CVE-2023-5063 — input validation issues | Upgrade to 0.12.5+ |
| `testcontainers` | 1.21.4 | **MEDIUM** | Outdated (current: 2.x) with known CVEs | Upgrade to 2.x |
| `spring-boot-starter-aop` | 3.5.12 | **MEDIUM** | Version mismatch — Spring Boot 3.x uses AOP 1.9.x, not 3.5.x | Remove version override |
| `dubbo` | 3.2.14 | LOW | Version conflict with `recommend-api` which uses dubbo 3.3.6 | Align versions |
| `mybatis-plus` | 3.5.16 | LOW | Slightly outdated (latest: 3.5.17+) | Minor update |
| `redisson` | 4.3.1 | LOW | Older version from 2022 | Minor update |
| `lombok` | 1.18.44 | LOW | Inconsistent with recommendation module (1.18.30) | Unify versions |
| `mapstruct` | 1.6.3 | LOW | Outdated — newer 1.6.x available | Minor update |

---

## 3. Technical Debt — TODO/NOTE Markers

### TODO Comments (9 total)

**Admin Features — Unimplemented:**

| File | Lines | Description |
|------|-------|-------------|
| `AdminAccountController.java` | 47 | Password change logic not implemented |
| `AdminAccountController.java` | 54 | Subscription retrieval not implemented |
| `AdminSettingsController.java` | 26, 53, 74, 92, 109, 131 | Settings persistence not implemented (6 endpoints) |
| `AdminSettingsController.java` | 147 | Cache clearing not implemented |

**Frontend:**

| File | Lines | Description |
|------|-------|-------------|
| `ModerationDetailDrawer.vue` | 192, 198 | Fetch reports/actions separately via API |
| `ChartTooltipContent.vue` | 28 | Chart rendering approach note |

### NOTE Comments (12 total — flag important technical concerns)

**Missing Database Migrations:**

| File | Lines | Description |
|------|-------|-------------|
| `ProblemListCategory.java` | 11 | Table does NOT exist in Prisma schema |
| `ProblemListBookmark.java` | 11 | Table does NOT exist in Prisma schema |
| `ProblemListBookmarkMapper.java` | 16 | Requires `problem_list_bookmarks` table |
| `ProblemListCategoryMapper.java` | 15 | Requires `problem_list_categories` table |
| `ProblemListServiceImpl.java` | 96, 423, 440, 465, 497 | Methods require missing tables |

**Performance/Architecture:**

| File | Lines | Description |
|------|-------|-------------|
| `SubmissionMapper.java` | 370 | MyBatis `${}` interpolation used intentionally (SQL injection risk — see Section 1) |
| `AdminUserAnalyticsServiceImpl.java` | 112 | Retention calculation is an approximation, not true set intersection |
| `AdminContentAnalyticsServiceImpl.java` | 78 | **N+1 issue**: tag loop queries per-problem submission counts |

---

## 4. Complexity Hotspots

### Backend — Large Service Classes (>450 lines)

| File | Lines | Concern |
|------|-------|---------|
| `SubmissionServiceImpl.java` | 682 | God service — submission handling, contest recording, achievement triggering, notifications, stats all in one class. 10 dependencies injected |
| `ModerationServiceImpl.java` | 578 | Handles 4 entities (ModerationQueue, Report, Appeal, UserBan). 6 mappers. Switch with 10 cases |
| `ProblemListServiceImpl.java` | 573 | 5 mappers. Missing table references cause broad catch blocks masking errors |
| `ProblemServiceImpl.java` | 561 | 4 mappers. JSON parsing logic repeated in multiple places. 10+ helper methods in build flow |
| `ContestController.java` | 541 | 40+ endpoints in single controller. Mixes admin CRUD, public queries, participation, virtual contests |
| `MonitoringServiceImpl.java` | 454 | Direct system access (JMX, JDBC, Redis). Noisy try-catch blocks. Raw SQL embedded in Java |
| `UserServiceImpl.java` | 452 | 4 mappers. User CRUD, stats, follows, profile all in one class |
| `SolutionServiceImpl.java` | 446 | Code review, vote, and solution management |
| `AdminSubmissionServiceImpl.java` | 444 | Admin submission management |

### Frontend — Large TypeScript Files (>400 lines)

| File | Lines | Concern |
|------|-------|---------|
| `console/src/api/contest.ts` | 559 | Massive snake_case to camelCase mapping. 20+ types defined inline. No separation of types/mappers/API |
| `console/src/api/problem-list.ts` | 528 | API layer with inline type definitions |
| `console/src/composables/useCodeTemplates.ts` | 527 | Large template management composable |
| `console/src/types/contest.ts` | 463 | Inline type definitions with complex mappings |
| `console/src/utils/request.ts` | 442 | Request deduplication, CSRF handling, interceptors — complex utility |
| `console/src/stores/contest.ts` | 424 | Pinia store with large state management |
| `console/src/lib/socket.ts` | 423 | WebSocket/SockJS handling with reconnection logic |
| `console/src/router/index.ts` | 416 | Route definitions |
| `console/src/stores/auth.ts` | 405 | Auth store with token management |

### Controller with Data Handling (Should Be Service)

| File | Lines | Concern |
|------|-------|---------|
| `AdminSettingsController.java` | 386 | 6 nested static DTO classes. Controller doing data transformation that belongs in a service |

---

## 5. Anti-Patterns

### N+1 Query Problems

| File | Lines | Description |
|------|-------|-------------|
| `AdminAnalyticsServiceImpl.java` | 69-74, 84-97, 104-107, 136-141 | Loop over contests, query `contestParticipantMapper.selectList()` inside — 100 contests = 100+ queries |
| `ForumPostServiceImpl.java` | 129 | `authorIds.forEach(aid -> userService.findById(aid))` — N+1 instead of batch fetch |

### Magic Numbers/Strings Without Constants

| File | Lines | Values |
|------|-------|--------|
| `AdminAnalyticsServiceImpl.java` | 112, 202-203, 234-235 | `100.0` (completion rate), `5.0` (churn), `2.5` (conversion), `9.99`, `79.99/12` |
| `AdminSettingsController.java` | 27-34, 82-85, 100-102, 117-124 | Rate limits `"100"`, `"10"`, `"5"`, `"20"`; upload `"10MB"`, `"jpg,jpeg,png,gif,pdf,zip"`; site name `"UltiCode"` |
| `AdminAccountController.java` | 56-57 | Subscription status strings `"FREE"`, `"ACTIVE"` instead of enum/constants |

### Duplicate Code

| Files | Description |
|-------|-------------|
| `ForumPostServiceImpl.java:173-186` & `ForumCommentServiceImpl.java:177-189` | Identical `ensureForumUserExists()` method |
| `AdminSettingsController.java:25-34` & `:39-47` | `getAllSettings()` and `getSettings()` repeat identical hardcoded defaults |

### Unbounded Collections / Memory Leak Risk

| File | Lines | Description |
|------|-------|-------------|
| `RealtimeService.java` | 52-55 | `lastRankingPushTime` and `pendingRankingUpdates` grow unbounded under high contest activity |
| `UserSessionManager.java` | 22, 25, 28 | Three ConcurrentHashMaps for sessions with no TTL eviction |

### Thread Safety Issues

| File | Lines | Description |
|------|-------|-------------|
| `RealtimeService.java` | 52 | `Map<String, Long> lastRankingPushTime` uses regular HashMap (not thread-safe) while other maps use ConcurrentHashMap |
| `RealtimeService.java` | 167-193 | `@Scheduled flushPendingRankings()` reads/writes `lastRankingPushTime` without synchronization |

### Caching Without Eviction

| File | Lines | Description |
|------|-------|-------------|
| `ProblemServiceImpl.java` | 145 | `@Cacheable` on `getProblemById()` — no `@CacheEvict` on updates |
| `UserServiceImpl.java` | 202 | `@Cacheable` on `getUserStatsById()` — no eviction policy |
| `ContestServiceImpl.java` | 191 | `@Cacheable` on `getGlobalRanking()` — ranking cached with no TTL |

### Incomplete Implementation Returning Fake Data

| File | Lines | Description |
|------|-------|-------------|
| `AdminAccountController.java` | 47, 54 | `changePassword()` returns success without doing anything; `getSubscription()` returns hardcoded fake SubscriptionVO |
| `AdminSettingsController.java` | 26-147 (all 7 endpoints) | All settings endpoints return hardcoded placeholder values, no database operations |

---

## 6. Dependency Version Inconsistencies

| Package | Console | Management | Issue |
|---------|---------|------------|-------|
| `eslint` | 9.39.4 | 10.2.1 | Different ESLint major versions across frontends |
| `eslint-plugin-vue` | 9.33.0 | 10.8.0 | Incompatible with console's ESLint 9.x per CLAUDE.md |
| `@unovis/ts` / `@unovis/vue` | 1.6.2 | 1.6.4 | Version split |
| `vue` | 3.5.25 | 3.5.31 | Minor version difference |
| `tailwindcss` | 4.1.17 | 4.2.2 | Minor version difference |
| `lombok` (backend vs recommendation) | 1.18.44 | 1.18.30 | Version mismatch across modules |

---

## 7. Additional Concerns (2026-04-30 Update)

### 7.1 Performance - Redis Connection Pool

**File:** `backend-spring/src/main/resources/application.yml` (Line 25)

**Severity:** Medium

```yaml
connectionPoolSize: 64
max-active: 8
```

With only 8 `max-active` Spring Redis connections and 64 Redisson pool size, this may be insufficient under high load. For a busy submission system processing concurrent code executions, consider increasing pool size.

---

### 7.2 Async Thread Pool Missing Configuration

**File:** `backend-spring/src/main/java/com/ulticode/modules/achievement/service/impl/AchievementTriggerServiceImpl.java`

**Severity:** Medium

12 `@Async` methods but no visible custom `TaskExecutor` bean. Spring's default `SimpleAsyncTaskExecutor` creates a new thread per task:

```java
@Async
public CompletableFuture<Void> triggerAchievementAsync(...) { ... }  // 12 methods
```

---

### 7.3 Code Execution Helper String Building

**File:** `backend-spring/src/main/java/com/ulticode/modules/submission/service/impl/CodeExecutionHelperImpl.java` (Lines 60-153)

**Severity:** Medium

Python/C/C++/Java wrappers built via string concatenation instead of templates:

```java
return "import json, sys, time\n" +
    code + "\n" +
    "input_data = json.loads(sys.stdin.read())\n" +
    // ... 15 more lines of concatenated strings
```

Makes code hard to maintain, test, and could introduce escaping issues.

---

### 7.4 Multiple ObjectMapper Instances

**Files:**
- `backend-spring/src/main/java/com/ulticode/common/config/RedisConfig.java:36`
- `backend-spring/src/main/java/com/ulticode/modules/solution/service/impl/SolutionServiceImpl.java:59`
- `recommendation/recommend-provider/.../CacheConfig.java:75`

**Severity:** Low

`ObjectMapper` instantiated multiple times instead of shared Spring bean.

---

### 7.5 Hardcoded Timezone Configuration

**File:** `backend-spring/src/main/resources/application.yml` (Line 50)

**Severity:** Low

```yaml
jackson:
  time-zone: Asia/Shanghai
```

Timezone hardcoded. Should be configurable for international deployments.

---

### 7.6 Inconsistent Authorization Patterns

**File:** Multiple controllers and services

**Severity:** Medium

Mix of `@PreAuthorize` annotations and manual `SecurityUtil.hasRole()` checks:

```java
// Annotation (good)
@PreAuthorize("hasAnyRole('MODERATOR', 'ADMIN', 'SUPER_ADMIN')")

// Manual (inconsistent)
if (!SecurityUtil.hasRole("ADMIN")) throw new BusinessException(ErrorCode.FORBIDDEN);
```

Manual checks in service layer can be bypassed if service is called directly.

---

### 7.7 CORS Default Origins

**File:** `backend-spring/src/main/resources/application.yml` (Line 6)

**Severity:** Low

```yaml
cors:
  allowed-origins: ${CORS_ALLOWED_ORIGINS:http://localhost:9002,http://localhost:9003}
```

Defaults to localhost - must be overridden in production.

---

### 7.8 Duplicate ErrorCode Definitions

**Files:**
- `backend-spring/src/main/java/com/ulticode/common/exception/ErrorCode.java` (199 lines, has HTTP status)
- `backend-spring/src/main/java/com/ulticode/common/constants/ErrorCode.java` (113 lines, no HTTP status)

**Severity:** Low

Two separate `ErrorCode` enums create confusion about which to use.

---

### 7.9 @SuppressWarnings Usage

**Files:** 9 Java files with 14 annotations

**Severity:** Low

Excessive suppression indicates raw types or unchecked casts being used.

---

### 7.10 N+1 Queries - AdminAnalytics

**File:** `backend-spring/src/main/java/com/ulticode/modules/admin/service/impl/AdminAnalyticsServiceImpl.java`

**Severity:** Medium

Loop-based queries for each contest, user, or problem instead of batch operations.

---

## Summary Table

| Category | Count | Critical Items |
|----------|-------|----------------|
| Security Risks | 9 | 5 critical (hardcoded secrets, SQL injection, CSRF) |
| Dependency Vulnerabilities | 10 | 2 critical (jjwt CVE, markdown-it-katex XSS) |
| TODO Comments | 9 | 7 admin feature stubs |
| NOTE Comments | 12 | 9 missing database tables, 2 performance concerns |
| Large/Complex Files | 17 | 8 backend services, 9 frontend files |
| Anti-Patterns | 6 types | N+1 (2), Magic strings (3), Duplicate (2), Memory leaks (2), Thread safety (2) |
| Untested Modules | 7 | bookmark, contest, forum, moderation, permission, problemlist, refreshtoken |

---

## Risk Matrix

| | Low Impact | Medium Impact | High Impact |
|---|---|---|---|
| **High Likelihood** | Frontend ESLint version split | Caching without eviction | SQL Injection (SubmissionMapper) |
| **Medium Likelihood** | Magic numbers | N+1 queries (AdminAnalytics) | Hardcoded secrets (JWT, DB password) |
| **Low Likelihood** | Duplicate code | Memory leaks (RealtimeService) | CSRF token exposure |

### Untested Module Risk

| Module | Likelihood | Impact | Combined Risk |
|--------|------------|--------|---------------|
| `contest` | Medium | High | **High** |
| `moderation` | Medium | High | **High** |
| `permission` | Low | High | Medium |
| `refreshtoken` | Low | High | Medium |

---

## Recommended Quick Wins

1. **Increase Redis pool size** — Change `connectionPoolSize: 64` to `256` and `max-active: 8` to `32`
2. **Configure async thread pool** — Add `@Bean TaskExecutor` with proper sizing for `@Async` methods
3. **Unify ErrorCode enums** — Pick one and migrate all references
4. **Fix RealtimeService thread safety** — Change `HashMap` to `ConcurrentHashMap`
5. **Add TTL to session caches** — `UserSessionManager` maps grow unbounded

---

## 8. Test Coverage Analysis (2026-05-01 Update)

### 8.1 Backend Modules Without Unit Tests

The following modules have **no test files** (out of 28 total modules):

| Module | Risk Assessment | Notes |
|--------|-----------------|-------|
| `bookmark` | Medium | User collection management |
| `contest` | **High** | Core competitive programming feature - 5 mappers, 3 services |
| `forum` | Medium | Community features - 9 mappers |
| `moderation` | **High** | Content safety - 6 mappers, handles bans/warnings/appeals |
| `permission` | **High** | Security-critical access control |
| `problemlist` | Medium | Problem organization |
| `refreshtoken` | **High** | Authentication token lifecycle |

**Total uncovered modules**: 7 of 28 (25%)

### 8.2 Modules With Minimal Test Coverage

| Module | Test Methods | Concern |
|--------|--------------|---------|
| `follow` | 4 | Small module but critical for social features |
| `notification` | 2 | Only listener test, no service tests |
| `achievement` | 23 | 1 service test + 1 listener test |
| `admin` | 11 | Only 2 services tested (Forum, Submission) |

### 8.3 Dependency Version Update (2026-05-01)

| Package | Previous Version | Current Version | Notes |
|---------|-----------------|-----------------|-------|
| `eslint` (console) | 9.39.4 | 9.30.1 | Per package.json |
| `eslint-plugin-vue` (console) | 9.33.0 | 9.30.0 | Per AGENTS.md requirement |
| `vue` (console) | 3.5.25 | 3.5.25 | Matches |
| `vue` (management) | 3.5.31 | 3.5.26 | Minor diff |
| `axios` | 1.14.0 | 1.13.2 | Version changed |

---

## 9. Security Configuration Assessment

**File**: `backend-spring/src/main/java/com/ulticode/common/config/SecurityConfig.java`

| Feature | Status | Notes |
|---------|--------|-------|
| JWT Stateless Auth | ✓ | Proper `SessionCreationPolicy.STATELESS` |
| CSRF Protection | ⚠ | Disabled - valid when using httpOnly JWT cookies |
| HSTS Headers | ✓ | `includeSubDomains(true)`, `maxAgeInSeconds(31536000)`, `preload(true)` |
| XSS Protection | ✓ | `XXssProtectionHeaderWriter.HeaderValue.ENABLED_MODE_BLOCK` |
| Content Security Policy | ✓ | Restrictive `default-src 'self'` with allowlisted externals |
| Frame Options | ✓ | `DENY` mode |
| Password Encoding | ✓ | `BCryptPasswordEncoder` bean |
| CORS | ✓ | Externalized via `CorsProperties` |

**JWT Authentication Filter**: `JwtAuthenticationFilter.java` properly validates tokens and handles exceptions (ExpiredJwtException, MalformedJwtException, etc.)
