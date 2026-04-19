# Domain Pitfalls: v1.5 Technical Debt Remediation

**Domain:** Spring Boot + MyBatis-Plus + Redis integration
**Researched:** 2026-04-19
**Confidence:** HIGH

---

## Critical Pitfalls

Mistakes that cause production issues or require significant rework.

### Pitfall 1: Rate Limiter Bypassed Under Load

**What goes wrong:** Rate limiting works at low traffic but fails under concurrent load.

**Why it happens:** Non-atomic check-then-acquire pattern creates race condition window.

**Consequences:** Limit exceeded by burst of requests that all see "available" simultaneously.

**Prevention:** Use Redisson's atomic `tryAcquire()`:

```java
// WRONG - race condition
if (rateLimiter.tryAcquire(1)) {  // Check
    // Another thread may have acquired here
    proceed();                     // Act
}

// CORRECT - atomic
if (rateLimiter.tryAcquire(1)) {  // Check+Act together
    proceed();
}
```

**Detection:** Load test with `wrk` or `ab` sending concurrent requests.

---

### Pitfall 2: Cache Stampede

**What goes wrong:** Cache miss causes multiple simultaneous requests to hit the database.

**Why it happens:** Multiple requests discover cache empty at the same time, all query DB.

**Consequences:** Database overwhelmed when popular content expires.

**Prevention:** Use cache-aside with jittered TTL:

```java
@Cacheable(value = "problems", key = "#id", unless = "#result == null")
public Optional<Problem> findById(Long id) {
    // Add small random delay to prevent all caches expiring at once
    return Optional.ofNullable(problemMapper.selectById(id));
}

// In Redis cache config, use jitter:
// config.setTTL(Duration.ofMinutes(5 + RandomUtils.nextInt(60)));
```

**Alternative:** Use Redisson's `getCached` with lock:

```java
V value = redissonClient.getCache("problems:" + id).get(key, expiry, loader);
```

---

### Pitfall 3: MyBatis-Plus N+1 in List Queries

**What goes wrong:** Fetching 100 problems triggers 101 queries (1 + 100 tag lookups).

**Why it happens:** MyBatis-Plus `selectById` doesn't auto-join relations. Accessing `problem.getTags()` in loop triggers lazy query per entity.

**Consequences:** O(n) database queries, unacceptable for large lists.

**Prevention:** Always analyze list queries with EXPLAIN:

```sql
EXPLAIN SELECT * FROM problems WHERE is_published = 1 LIMIT 20;
-- If query count > 1 for a list endpoint, N+1 exists
```

**Fix:** Use JOIN in XML mapper or batch fetch service-side.

---

### Pitfall 4: Coverage Gate Blocking All Work

**What goes wrong:** JaCoCo fails build at 30% coverage, blocking all commits.

**Why it happens:** Setting 80% threshold immediately on legacy codebase with no coverage.

**Consequences:** Team cannot merge any code until coverage is raised.

**Prevention:** Start low, increment gradually:

```xml
<!-- Phase 1: 30% to establish baseline -->
<minimum>0.30</minimum>

<!-- Phase 2: 40% after 3 months -->
<minimum>0.40</minimum>

<!-- Phase 3: 50% after 6 months -->
<minimum>0.50</minimum>
```

---

## Moderate Pitfalls

### Pitfall 5: Cache Invalidation Missing on Updates

**What goes wrong:** Updated problem shows stale data for up to TTL duration.

**Why it happens:** `@CacheEvict` not added to all mutation methods.

**Prevention:** Audit all create/update/delete methods:

```java
@CacheEvict(value = "problems", key = "#result.id")  // On update
public Problem updateProblem(Long id, UpdateProblemDTO dto) { }

@CacheEvict(value = "problems", key = "#id")         // On delete
public void deleteProblem(Long id) { }

@CacheEvict(value = "problems", allEntries = true)   // On bulk operation
public void importProblems(List<ProblemDTO> problems) { }
```

---

### Pitfall 6: Entity Objects in Cache

**What goes wrong:** Cached entity mutated elsewhere, corrupting cache.

**Why it happens:** Returning raw entity from `@Cacheable` method.

**Prevention:** Return DTOs/records, not entities:

```java
// WRONG - entity in cache
@Cacheable(value = "problems", key = "#id")
public Problem findById(Long id) {
    return problemMapper.selectById(id);  // Returns entity
}

// CORRECT - DTO in cache
@Cacheable(value = "problems", key = "#id")
public ProblemVO findById(Long id) {
    return ProblemVO.from(problemMapper.selectById(id));  // Returns copy
}
```

---

### Pitfall 7: Redis Connection Exhaustion

**What goes wrong:** Too many open Redis connections crashes rate limiter and cache.

**Why it happens:** Creating new RedissonClient per request instead of reusing singleton.

**Prevention:** RedissonClient is thread-safe singleton:

```java
@Configuration
public class RedisConfig {
    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient() {
        return Redisson.create(config);
    }
}
```

---

### Pitfall 8: MyBatis XML Changes Breaking Existing Queries

**What goes wrong:** Modifying XML mapper breaks existing functionality.

**Why it happens:** No test coverage on mapper XML queries.

**Prevention:**
1. Write integration tests for each mapper query
2. Use MyBatis-Plus wrapper for simple queries (less error-prone)
3. Keep XML changes minimal and targeted

---

## Minor Pitfalls

### Pitfall 9: Rate Limit Key Collision

**What goes wrong:** Different endpoints share same rate limit key.

**Why it happens:** Key only includes user ID, not endpoint path.

**Prevention:** Include endpoint in key:

```java
String key = "rate:user:" + userId + ":" + request.getRequestURI();
```

---

### Pitfall 10: Coverage Exclusions Too Broad

**What goes wrong:** Excluding too many classes lowers effective coverage.

**Why it happens:** Overzealous exclusion of entities, DTOs.

**Prevention:** Only exclude truly untestable code:

| Exclude | Yes/No | Reason |
|---------|--------|--------|
| Application.java | Yes | Entry point |
| *Config.java | Yes | Framework config |
| *Exception.java | Marginal | May contain business logic |
| *DTO.java | No | May have validation logic |
| *Entity.java | No | Domain logic in getters |
| *Mapper.java | Yes | MyBatis generated |

---

### Pitfall 11: Hardcoded Cache TTLs

**What goes wrong:** 5-minute TTL everywhere, regardless of data change frequency.

**Why it happens:** Copy-paste cache configuration.

**Prevention:** Match TTL to data volatility:

| Data Type | TTL | Rationale |
|-----------|-----|-----------|
| User session | 30 min | User activity period |
| Problem detail | 10 min | Rarely changes |
| Leaderboard | 1 min | Updates on every submission |
| Tag list | 30 min | Rarely changes |

---

## Phase-Specific Warnings

| Phase Topic | Likely Pitfall | Mitigation |
|-------------|---------------|------------|
| Rate Limiting | Race conditions | Use atomic tryAcquire only |
| Caching | Stampede on miss | Jittered TTL, cache-aside locking |
| N+1 Fixes | Breaking existing queries | Add mapper tests before changes |
| JaCoCo | Coverage gate blocking work | Start at 30%, increment gradually |

---

## Sources

- Context7: Redisson rate limiter documentation
- Context7: Spring Cache best practices
- Context7: MyBatis-Plus N+1 patterns
- Official JaCoCo documentation on exclusions
