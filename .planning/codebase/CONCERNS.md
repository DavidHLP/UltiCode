# Codebase Concerns

**Analysis Date:** 2026-04-19

## Tech Debt

### TD-01: PM2 Environment Variable Parsing
**Severity:** MEDIUM
**Files:** `ecosystem.config.cjs`

**Issue:** Custom .env file parser uses string manipulation that may fail with:
- Quoted values with nested quotes
- Values containing `=` characters
- Multiline values
- Special characters in passwords/tokens

**Impact:** JWT_SECRET and REDIS_PASSWORD may be lost on PM2 restart without `--update-env`.

**Fix approach:** Replace custom parser with proven `dotenv` package or use PM2's built-in dotenv support.

---

### TD-02: Maven Build Order Dependency
**Severity:** MEDIUM
**Files:** `backend-spring/pom.xml`, `recommendation/pom.xml`

**Issue:** `backend-spring` depends on `com.ulticode:recommend-api:jar:1.0.0` which is a local Maven module. If `recommendation` is not installed first via `mvn install -DskipTests`, backend build fails.

**Impact:** Fresh builds and CI require specific build order.

**Fix approach:** Either publish `recommend-api` to a private Maven repository, use Maven reactor to build in correct order, or document the build order requirement.

---

### TD-03: Dubbo Configuration Complexity
**Severity:** MEDIUM
**Files:** Dubbo configs in `recommendation/` module

**Issue:** Dubbo WARN messages about `empty url address list` and `empty configurators` require specific configuration workarounds:
```
enable-empty-protection: "true"  # Must be in dubbo.registry.parameters map
```

**Impact:** Added complexity in configuration, potential runtime issues if misconfigured.

**Fix approach:** Document exact configuration requirements and verify in production.

---

### TD-04: Large Files Over 500 Lines
**Severity:** LOW
**Files:**
- `backend-spring/src/main/java/com/ulticode/modules/forum/service/impl/ForumServiceImpl.java` (693 lines)
- `backend-spring/src/main/java/com/ulticode/modules/submission/service/CodeExecutionService.java` (643 lines)
- `backend-spring/src/main/java/com/ulticode/modules/contest/service/impl/ContestServiceImpl.java` (626 lines)
- `backend-spring/src/main/java/com/ulticode/modules/submission/service/impl/SubmissionServiceImpl.java` (591 lines)
- `backend-spring/src/main/java/com/ulticode/modules/moderation/service/impl/ModerationServiceImpl.java` (578 lines)

**Issue:** Multiple service implementations exceed 500 lines, violating the 800-line max guideline.

**Fix approach:** Extract related methods into separate service classes or utility classes.

---

### TD-05: Frontend Large Files
**Severity:** LOW
**Files:**
- `console/src/composables/contest/useContestSocket.ts` (608 lines)
- `console/src/api/contest.ts` (559 lines)
- `management/src/views/moderation/columns.ts` (533 lines)
- `console/src/api/problem-list.ts` (528 lines)
- `console/src/composables/useCodeTemplates.ts` (527 lines)

**Issue:** Some TypeScript/Vue files exceed 500 lines.

**Fix approach:** Extract sub-modules, split hooks by concern, use barrel exports.

---

## Known Bugs

### B-01: Swagger/Springdoc Disabled
**Severity:** HIGH
**Files:** `backend-spring/src/main/java/com/ulticode/common/config/SwaggerConfig.java`

**Symptoms:** API documentation completely disabled. Accessing `/swagger-ui.html` or `/api-docs` returns nothing.

**Root Cause:** springdoc 2.x is incompatible with Spring Boot 3.2.5 (Missing `LiteWebJarsResourceResolver` class). The `SwaggerConfig` class is fully commented out.

**Fix approach:** Upgrade to a compatible springdoc version when available, or find alternative API documentation solution.

---

### B-02: Admin Forum Stats Return Hardcoded Zeros
**Severity:** MEDIUM
**Files:** `backend-spring/src/main/java/com/ulticode/modules/admin/service/impl/AdminForumServiceImpl.java` (lines 276-278)

**Symptoms:** Admin dashboard shows incorrect forum engagement metrics (commentCount, upvotes, downvotes all show 0).

**Root Cause:** Forum post statistics are hardcoded to 0 instead of querying actual data:
```java
vo.setCommentCount(0); // TODO: Query from forum_comments table
vo.setUpvotes(0); // TODO: Query from forum_votes table
vo.setDownvotes(0); // TODO: Query from forum_votes table
```

**Fix approach:** Implement actual queries against `forum_comments` and `forum_votes` tables.

---

## Security Considerations

### SEC-01: SELECT * Queries in Mappers
**Severity:** INFO
**Files:** `backend-spring/src/main/java/com/ulticode/modules/contest/mapper/*.java`

**Observation:** Multiple mapper queries use `SELECT *` which could return unexpected columns if the schema changes.

**Current Mitigation:** MyBatis-Plus entity mappings are explicit; SELECT * is used with known table structures.

**Recommendations:** Consider specifying columns explicitly for better schema contract enforcement.

---

### SEC-02: System.out.println in Code Execution
**Severity:** LOW
**Files:** `backend-spring/src/main/java/com/ulticode/modules/submission/service/CodeExecutionService.java:506`

**Observation:** `System.out.print(result)` found in code execution path.

**Risk:** Could leak submission output to stdout logs.

**Fix approach:** Replace with proper logging framework.

---

## Performance Bottlenecks

### PERF-01: N+1 Query Potential in Contest Mappers
**Severity:** MEDIUM
**Files:** `backend-spring/src/main/java/com/ulticode/modules/contest/mapper/*.java`

**Issue:** Multiple mappers use `SELECT *` with no explicit JOINs, suggesting data may be fetched in separate queries.

**Example:**
```java
@Select("SELECT * FROM contest_submissions WHERE contest_id = #{contestId} AND participant_id = #{participantId} ORDER BY submitted_at ASC")
```

**Fix approach:** Use JOIN queries or batch fetch to reduce round trips for contest rankings and submissions.

---

## Fragile Areas

### FRAG-01: JWT Token Provider Returns Null
**Severity:** MEDIUM
**Files:** `backend-spring/src/main/java/com/ulticode/security/jwt/JwtTokenProvider.java:108`

**Observation:** `return null` found in token validation path. Multiple `return null` statements in authentication filters.

**Why fragile:** Silent authentication failures could cause confusing UX where users appear logged out intermittently.

**Safe modification:** Ensure all null returns are properly logged and handled by callers.

---

### FRAG-02: Redis Service Returns Null
**Severity:** LOW
**Files:** `backend-spring/src/main/java/com/ulticode/infrastructure/redis/RedisService.java` (multiple return null points)

**Observation:** Multiple `return null` statements in cache operations.

**Why fragile:** Cache misses returning null could be confused with errors.

**Safe modification:** Consider using `Optional` for cache operations or ensure callers distinguish null from cache miss.

---

### FRAG-03: Volatile Counter in Monitoring
**Severity:** LOW
**Files:** `backend-spring/src/main/java/com/ulticode/modules/monitoring/service/impl/MonitoringServiceImpl.java:58`

**Observation:** `private volatile long queryCount = 0;`

**Why fragile:** Volatile is insufficient for counter operations requiring atomic increment.

**Safe modification:** Use `AtomicLong` or proper synchronization for counter increments.

---

## Scaling Limits

### SCALE-01: No Caching Annotations Detected
**Severity:** MEDIUM
**Files:** None using `@Cacheable`, `@CacheEvict`, `@CachePut`

**Issue:** No Spring caching annotations found in the codebase. Every request for frequently accessed data (problems, user stats, contest rankings) hits the database.

**Impact:** High traffic could overwhelm MySQL.

**Fix approach:** Add caching for read-heavy operations like problem lists, user profiles, and contest data.

---

### SCALE-02: Forum Service Size
**Severity:** MEDIUM
**Files:** `backend-spring/src/main/java/com/ulticode/modules/forum/service/impl/ForumServiceImpl.java` (693 lines)

**Issue:** One of the largest service files, handling posts, comments, votes, and community membership.

**Limit:** As forum grows, this service will become a bottleneck.

**Fix approach:** Split into separate services: `ForumPostService`, `ForumCommentService`, `ForumVoteService`, `CommunityMembershipService`.

---

## Dependencies at Risk

### DEPS-01: springdoc OpenAPI
**Severity:** HIGH
**Issue:** springdoc 2.x incompatible with Spring Boot 3.2.5, causing Swagger to be disabled.

**Impact:** No API documentation available.

**Migration path:** Monitor springdoc releases for Spring Boot 3.2.x compatibility, or consider switching to springdoc 3.x or alternative like SpringDoc OpenAPI (springdoc-openapi v3).

---

## Missing Critical Features

### MISS-01: No Rate Limiting Implementation
**Severity:** HIGH
**Observation:** `@RateLimit` annotation exists in `backend-spring/src/main/java/com/ulticode/common/annotation/RateLimit.java` but no implementation or configuration found.

**Blocks:** Protection against API abuse and DDoS.

**Fix approach:** Implement rate limiting using Redis or a dedicated rate limiter.

---

### MISS-02: No Test Coverage Enforcement
**Severity:** MEDIUM
**Observation:** While tests exist, no coverage enforcement (JaCoCo configuration with thresholds) found in build.

**Blocks:** Unknown coverage percentage, potential untested paths in production.

**Fix approach:** Add JaCoCo with coverage thresholds to Maven build.

---

## Test Coverage Gaps

### TEST-01: Admin Forum Stats Untested
**Severity:** MEDIUM
**What's not tested:** `AdminForumServiceImpl.getPostDetail()` - the hardcoded zeros are never tested with real data.

**Files:** `backend-spring/src/main/java/com/ulticode/modules/admin/service/impl/AdminForumServiceImpl.java`

**Risk:** Stats will display incorrect data until fixed.

---

### TEST-02: Code Execution Service
**Severity:** MEDIUM
**What's not tested:** `CodeExecutionService` (643 lines) - critical path for code judging with `System.out.print` debug statement.

**Files:** `backend-spring/src/main/java/com/ulticode/modules/submission/service/CodeExecutionService.java`

**Risk:** Submission output leakage, potential security issues in code execution sandbox.

---

### TEST-03: Contest Scheduling
**Severity:** MEDIUM
**What's not tested:** `ContestScheduler` returns null on certain conditions.

**Files:** `backend-spring/src/main/java/com/ulticode/modules/contest/scheduler/ContestScheduler.java:60`

**Risk:** Scheduled contests may not start or end properly.

---

## CI/CD Issues

### CI-01: Flyway Download URL Obsolete
**Severity:** HIGH
**Files:** `.github/workflows/ci.yml`

**Issue:** CI workflow downloads Flyway from `https://download.redgate.com/flyway/...` but Flyway has moved to Redgate domain. URL returns 404.

**Status:** Issue identified in CI run 24601704434, fix was proposed but verification needed.

**Fix approach:** Update Flyway download URL in CI workflow.

---

### CI-02: Build Artifact Caching Gaps
**Severity:** LOW

**Issue:** Maven and pnpm dependencies are downloaded on every CI run instead of being cached effectively.

**Impact:** Longer CI execution times.

**Fix approach:** Implement proper caching strategy for Maven/pnpm artifacts.

---

## Already Resolved (Historical Reference)

These issues were identified and fixed but documented for awareness:

- **ESLint version conflict:** ESLint 10.x incompatible with @typescript-eslint/utils 8.x - Fixed by downgrading to eslint ^9.30.1
- **vitest setupFiles reference:** References non-existent `console/src/test/setup.ts` - Fixed by removing setupFiles
- **recommend-api not installed:** CI Backend Build failed - Fixed by ensuring proper build order
- **OAuthService Spring 6.x compatibility:** Fixed for Spring Framework 6.x compatibility
- **springdoc LiteWebJarsResourceResolver:** Downgraded from 2.7.0 to 2.6.0
- **CI lockfile issues:** Multiple CI fixes applied and verified (commit aa51e0404)

---

## Priority Summary

| ID | Severity | Category | Item |
|----|----------|----------|------|
| B-01 | HIGH | Bug | Swagger disabled |
| CI-01 | HIGH | CI/CD | Flyway URL obsolete |
| SEC-01 | HIGH | Security | Rate limiting not implemented |
| DEPS-01 | HIGH | Dependency | springdoc incompatibility |
| B-02 | MEDIUM | Bug | Forum stats hardcoded |
| TD-01 | MEDIUM | Tech Debt | PM2 env parsing |
| TD-02 | MEDIUM | Tech Debt | Maven build order |
| TD-03 | MEDIUM | Tech Debt | Dubbo configuration |
| PERF-01 | MEDIUM | Performance | N+1 query potential |
| SCALE-01 | MEDIUM | Scaling | No caching annotations |
| SCALE-02 | MEDIUM | Scaling | Forum service size |
| MISS-02 | MEDIUM | Missing | No test coverage enforcement |
| TEST-01 | MEDIUM | Testing | Admin forum stats untested |
| TEST-02 | MEDIUM | Testing | Code execution untested |
| TEST-03 | MEDIUM | Testing | Contest scheduler untested |
| TD-04 | LOW | Tech Debt | Large Java files |
| TD-05 | LOW | Tech Debt | Large frontend files |
| SEC-02 | LOW | Security | System.out.println in code exec |
| FRAG-01 | LOW | Fragile | JWT null returns |
| FRAG-02 | LOW | Fragile | Redis null returns |
| FRAG-03 | LOW | Fragile | Volatile counter |
| CI-02 | LOW | CI/CD | Build caching gaps |

---

*Concerns audit: 2026-04-19*
