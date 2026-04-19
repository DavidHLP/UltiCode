# Feature Landscape: Technical Debt Remediation (v1.5)

**Domain:** Online Judge Platform Remediation
**Researched:** 2026-04-20
**Confidence:** MEDIUM-HIGH (based on existing codebase analysis)

## Remediation Categories

Based on CONCERNS.md analysis, the 23 issues fall into these categories:

| Category | Count | Priority |
|----------|-------|----------|
| Performance (N+1, Caching) | 3 | HIGH |
| Missing Infrastructure (Rate Limit, JaCoCo) | 2 | HIGH |
| Security (System.out, Hardcoded Zeros) | 3 | MEDIUM |
| Tech Debt (Build Order, Config) | 3 | MEDIUM |
| Large Files | 2 | LOW |
| Fragile Code | 3 | LOW |
| CI/CD | 2 | MEDIUM |

---

## Category 1: Rate Limiting with Redis

**Severity:** HIGH (MISS-01)
**Status:** Annotation exists, implementation missing

### Current State

```java
// backend-spring/src/main/java/com/ulticode/common/annotation/RateLimit.java
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {
    String key() default "";
    int limit() default 100;
    int period() default 60;
}
```

`RateLimitAspect.java` exists but needs Redis-backed implementation with Lua script for atomicity.

### What "Done" Looks Like

- [ ] All public endpoints annotated with `@RateLimit` (key, limit, period)
- [ ] RateLimitAspect implemented using Redisson RRateLimiter
- [ ] Lua script executes atomically (no race conditions)
- [ ] Returns 429 Too Many Requests with `Retry-After` header when exceeded
- [ ] Rate limit key includes user ID (authenticated) or IP (anonymous)
- [ ] Graceful degradation if Redis unavailable (allow request, log warning)

### Complexity: MEDIUM

**Dependencies:** Redis connection (already exists in `RedisService`), `spring-boot-starter-cache`

---

## Category 2: JaCoCo Coverage Enforcement

**Severity:** MEDIUM (MISS-02)
**Status:** Tests exist, no enforcement

### Current State

Tests exist in `backend-spring/src/test/java/` but:
- No JaCoCo plugin in pom.xml
- No coverage thresholds
- Unknown coverage percentage

### What "Done" Looks Like

- [ ] JaCoCo plugin configured in `backend-spring/pom.xml`
- [ ] Minimum coverage thresholds:
  - Line coverage: 50% (initial), target 60%
  - Branch coverage: 40% (initial), target 50%
- [ ] Exclusions configured:
  - `*Application.java`
  - `*Config.java`
  - `*Exception.java`
  - `*VO.java`, `*DTO.java`, `*Request.java`, `*Response.java`
  - Generated mapper classes
- [ ] Maven fails build if coverage below threshold (`mvn verify`)
- [ ] Coverage report generated at `target/site/jacoco/index.html`

### Complexity: LOW

**Dependencies:** `jacoco-maven-plugin`, no external services needed

---

## Category 3: Redis Caching for Read-Heavy Queries

**Severity:** MEDIUM (SCALE-01)
**Status:** No caching annotations found anywhere

### Current State

Every request hits MySQL:
- Problem lists
- User profiles
- Contest rankings
- Forum posts

### What to Cache

| Data | TTL | Invalidation |
|------|-----|--------------|
| Problem list (public) | 5 min | On problem create/update |
| Problem detail | 10 min | On problem update |
| User profile | 15 min | On profile update |
| Contest rankings | 1 min | On new submission |
| Contest details | 5 min | On contest update |
| Forum post list | 2 min | On new post/comment |
| Tag list | 30 min | On tag create |

### What "Done" Looks Like

- [ ] Spring Cache abstraction enabled (`spring-boot-starter-cache`)
- [ ] `@Cacheable` on service methods returning read-heavy data
- [ ] `@CacheEvict` on mutation methods (create/update/delete)
- [ ] `@CachePut` when updating changes the cached value
- [ ] Cache key follows pattern: `{entity}:{id}` or `{entity}:list:{query-hash}`
- [ ] Redis as cache backend (already configured in `RedisConfig`)
- [ ] Cache errors do not crash requests (fallback to DB)

### Complexity: MEDIUM

**Dependencies:** `spring-boot-starter-cache`, existing RedisTemplate

---

## Category 4: N+1 Query Fixes

**Severity:** MEDIUM (PERF-01)
**Status:** Contest mappers identified, likely other modules

### Current State

```java
// ContestMapper - potential N+1
@Select("SELECT * FROM contest_submissions WHERE contest_id = #{contestId} ...")
List<ContestSubmission> selectByContestId(...);
// If ContestSubmission has nested objects fetched separately
```

### Fix Strategy: JOIN + Batch Fetch

| Approach | When to Use |
|----------|-------------|
| **JOIN FETCH** | Single query with related entities (1:1, N:1) |
| **@BatchSize** | Collection fetching (1:N) - MyBatis-Plus batches |
| **Custom DTO projection** | Read-only queries returning multiple entities |

### What "Done" Looks Like

- [ ] Contest rankings: Single query with JOIN FETCH for participant data
- [ ] Submission list: Batch fetch or JOIN for problem details
- [ ] Problem list: JOIN for difficulty/tags if needed
- [ ] No lazy loading triggers in list queries
- [ ] EXPLAIN ANALYZE confirms single-digit query count per request

### Complexity: MEDIUM

**Tools needed:** MyBatis-Plus `QueryWrapper` with JOINs, or MyBatis XML with resultMaps

---

## MVP Recommendation

Prioritize in this order:

### Phase 1: Rate Limiting (HIGH Priority)
1. Implement Redisson RRateLimiter in `RateLimitAspect`
2. Add `@RateLimit` to all public API endpoints
3. Test 429 responses and header behavior

### Phase 2: JaCoCo Setup (MEDIUM Priority)
1. Add JaCoCo plugin to `pom.xml`
2. Configure exclusions and thresholds
3. Verify current coverage baseline
4. Set initial thresholds (50% line, 40% branch)

### Phase 3: Security Fixes (MEDIUM Priority)
1. Replace `System.out.println` with proper logging
2. Fix hardcoded forum stats
3. Add tests for fixed paths

### Phase 4: Caching Layer (MEDIUM Priority)
1. Enable Spring Cache
2. Add `@Cacheable` to problem/user/contest queries
3. Add `@CacheEvict` to mutations

### Phase 5: N+1 Fixes (MEDIUM Priority)
1. Audit all mapper queries for N+1 potential
2. Add JOIN FETCH or batch size to contest rankings
3. Verify with EXPLAIN ANALYZE

### Phase 6: Large File Refactoring (LOW Priority)
1. Split ForumServiceImpl
2. Split CodeExecutionService
3. Split ContestServiceImpl

---

## Dependencies on Existing Infrastructure

| Component | Already Exists | What's Needed |
|-----------|---------------|---------------|
| Redis | Yes (`RedisService`) | Rate limit Lua script, cache config |
| RateLimit annotation | Yes | Implementation in Aspect |
| MyBatis-Plus | Yes | JOIN optimization in XML |
| Spring Cache | No | Add starter, configure Redis backend |
| JaCoCo | No | Add Maven plugin |

---

## Sources

- CONCERNS.md (v1.5 backlog, 2026-04-19)
- RateLimitAspect.java (existing implementation stub)
- RedisService.java (existing Redis operations)
- pom.xml (existing dependencies)
