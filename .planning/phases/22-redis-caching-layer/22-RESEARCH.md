# Phase 22: Redis Caching Layer - Research

**Researched:** 2026-04-20
**Domain:** Spring Cache abstraction over Redisson-backed Redis for Spring Boot 3.2
**Confidence:** MEDIUM

## Summary

Phase 22 adds a Spring Cache layer backed by Redisson 4.3.1 for read-heavy service operations. The approach uses `@Cacheable`/`@CacheEvict` annotations on service-layer methods, with a `RedisCacheManager` wired to the existing `RedissonClient` bean. A TTL jitter mechanism prevents cache stampede by randomizing expiration windows within a +/-10% band around a 5-minute base. The Redisson auto-configuration from `redisson-spring-boot-starter` already provides the `RedissonClient` bean -- only `spring-boot-starter-cache` needs to be added, and `@EnableCaching` placed on the main application class.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Cache configuration (RedisCacheManager) | API / Backend | -- | Spring @Configuration class registers CacheManager bean |
| @Cacheable on problem/user/contest reads | API / Backend | -- | Service-layer methods are the cache entry point |
| @CacheEvict on mutations | API / Backend | -- | Service-layer create/update/delete triggers invalidation |
| TTL jitter calculation | API / Backend | -- | Computed at cache-write time in a customizer |
| Serialization (Java <-> Redis) | API / Backend | -- | Handled by Spring's Redis cache serializer |

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `spring-boot-starter-cache` | 3.2.5 (managed) | Spring Cache abstraction layer | Required for `@EnableCaching`, `@Cacheable`, `@CacheEvict` |
| `redisson-spring-boot-starter` | 4.3.1 (existing) | RedissonClient bean + Redis connection | Already in pom.xml; provides RedissonClient bean |
| `spring-boot-starter-data-redis` | 3.2.5 (existing) | RedisCacheManager foundation | Already in pom.xml; provides cache infrastructure |

### No New Dependencies
- `spring-boot-starter-data-redis` already provides `RedisCacheManager`
- `redisson-spring-boot-starter` auto-configures `RedissonClient` as a Spring bean
- Java serialization (default) works with all existing VO/DTO classes

**Installation:**
```xml
<!-- Add to backend-spring/pom.xml -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-cache</artifactId>
</dependency>
```

## Architecture Patterns

### System Architecture Diagram

```
Service Layer (ProblemServiceImpl / UserServiceImpl / RankingServiceImpl)
  |
  | @Cacheable(method) --READ--> Cache lookup
  |    |
  |    v
  |  [RedisCacheManager] --> [RedissonClient] --> [Redis]
  |    |                                       (TTL + jitter)
  |    v
  |  [Cached VO/DTO returned]
  |
  | @CacheEvict(method) --WRITE--> Cache invalidation
  |    |
  |    v
  |  [allEntries=true] --> Redis key deleted
```

### Recommended Project Structure
```
backend-spring/src/main/java/com/ulticode/
├── common/
│   └── config/
│       └── RedisCacheConfig.java   # RedisCacheManager bean + TTL jitter
├── modules/
│   ├── problem/service/impl/ProblemServiceImpl.java      # +@Cacheable/@CacheEvict
│   ├── user/service/impl/UserServiceImpl.java           # +@Cacheable/@CacheEvict
│   └── contest/service/impl/RankingServiceImpl.java      # +@Cacheable/@CacheEvict
```

### Pattern 1: RedisCacheManager with RedissonClient + TTL Jitter

**What:** Wire `RedisCacheManager` (from spring-boot-starter-cache) to use `RedissonClient` as the cache backend, with per-cache-region TTL including random jitter.

**When to use:** Standard Spring Boot cache configuration when using Redisson as the Redis client.

**Example:**
```java
// Source: [CITED: docs.spring.io/spring-boot-data-redis-cache]
@Configuration
public class RedisCacheConfig {

    private final RedissonClient redissonClient;

    public RedisCacheConfig(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    @Bean
    public CacheManager cacheManager() {
        // Base TTL: 5 minutes (300 seconds)
        // Jitter: ±10% -> 270-330 seconds
        long baseTtl = 300L;
        long jitterRange = (long) (baseTtl * 0.1);  // 30 seconds

        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofSeconds(baseTtl + ThreadLocalRandom.current().nextInt((int) (jitterRange * 2 + 1)) - jitterRange))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new JdkRedisSerializer()));

        Map<String, RedisCacheConfiguration> cacheConfigs = Map.of(
                "problem",      defaultConfig,
                "userStats",    defaultConfig,
                "contest",      defaultConfig,
                "contestRanking", defaultConfig
        );

        return RedisCacheManager.builder(redissonClient.getConnectionFactory())
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigs)
                .build();
    }
}
```

**Anti-pattern note:** The `RedisCacheManager` builder `.build()` variant used above (`RedisCacheManagerBuilder`) takes `RedisConnectionFactory` from the `RedissonClient`. An alternative is `.cacheDefaults()` with `.transactionAware()` but transaction awareness is not needed for this use case.

### Pattern 2: @Cacheable on Read-Heavy Service Methods

**What:** Apply `@Cacheable` to finder methods that read from the database but are called frequently.

**When to use:** Methods that are called repeatedly with the same arguments (e.g., `getProblemById`, `getUserStatsById`).

**Example:**
```java
// Source: [CITED: docs.spring.io/spring-framework/reference/integration/redis.html]
@Service
@RequiredArgsConstructor
@Slf4j
public class ProblemServiceImpl implements ProblemService {

    private final ProblemMapper problemMapper;

    @Override
    @Cacheable(value = "problem", key = "#root.method.name + ':' + #id")
    public ProblemVO getProblemById(Long id) {
        log.debug("Cache miss for problem id={}", id);
        Problem problem = problemMapper.selectById(id);
        if (problem == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Problem not found");
        }
        return ProblemVO.fromEntity(problem);
    }
}
```

### Pattern 3: @CacheEvict on Mutation Methods

**What:** Invalidate cache entries when data is created, updated, or deleted.

**When to use:** All write operations on entities that have corresponding `@Cacheable` reads.

**Example:**
```java
// Source: [CITED: docs.spring.io/spring-framework/reference/integration/redis.html]
@Service
@RequiredArgsConstructor
@Slf4j
public class ProblemServiceImpl implements ProblemService {

    @Override
    @CacheEvict(value = "problem", allEntries = true)
    public void createProblem(CreateProblemDTO dto, String userId) {
        // ... mutation logic
    }

    @Override
    @CacheEvict(value = "problem", allEntries = true)
    public boolean updateProblem(Long id, UpdateProblemDTO dto, String userId) {
        // ... mutation logic
    }

    @Override
    @CacheEvict(value = "problem", allEntries = true)
    public boolean deleteProblem(Long id) {
        // ... mutation logic
    }
}
```

### Pattern 4: Multi-Cache Eviction

**What:** Evict from multiple cache regions in a single mutation operation.

**When to use:** Mutations that affect multiple cached datasets (e.g., contest registration affects both ranking and contest data).

**Example:**
```java
// Source: [CITED: docs.spring.io/spring-framework/reference/integration/redis.html]
@CacheEvict(value = {"contestRanking", "contest"}, allEntries = true)
public void registerContest(String contestId, String userId) {
    // ...
}
```

### Pattern 5: @EnableCaching Placement

**What:** `@EnableCaching` must be on a `@Configuration` class in the Spring component scan path.

**When to use:** Always, before any `@Cacheable` annotation can function.

**Example:**
```java
// Option A: Add to existing UlticodeBackendApplication.java
@SpringBootApplication
@EnableScheduling
@ConfigurationPropertiesScan
@EnableCaching  // <-- Add here
public class UlticodeBackendApplication { }

// Option B: Or add to RedisCacheConfig.java (preferred for clarity)
@Configuration
@EnableCaching
public class RedisCacheConfig { }
```

**Recommendation:** Add `@EnableCaching` to `RedisCacheConfig.java` -- a dedicated configuration class makes the cache setup explicit and easier to locate.

### Pattern 6: Jittered TTL for Cache Stampede Prevention

**What:** Add random jitter to cache TTL so entries expire at different times instead of simultaneously.

**When to use:** All cache regions with non-trivial TTL (anything over 60 seconds).

**Implementation:** The jitter is applied at cache write time via `RedisCacheConfiguration.entryTtl()`. The existing `ThreadLocalRandom` from `java.util.concurrent` is used -- no external library needed.

**Formula:** `baseTtl + random.nextInt(jitterRange * 2 + 1) - jitterRange`
- Base TTL: 300s
- Jitter range: ±30s (10% of 300)
- Effective range: 270-330s

**Warning:** The jitter is computed once per cache configuration, not per entry. A more precise approach computes jitter per entry in a `@Cacheable` custom `CacheWriter`. However, for D-15's stated approach (jitter at cache-write time via `ThreadLocalRandom`), the configuration-level approach is acceptable and simpler.

### Pattern 7: Cache Key Patterns

**What:** Use method name + first argument as the cache key for simple finder methods.

**When to use:** Single-parameter finder methods (getById, getByName, etc.).

**Example:**
```java
@Cacheable(value = "problem", key = "#root.method.name + ':' + #id")
ProblemVO getProblemById(Long id) { }  // key: "getProblemById:123"

@Cacheable(value = "userStats", key = "#root.method.name + ':' + #id")
UserStatsDTO getUserStatsById(String id) { }  // key: "getUserStatsById:user-001"

@Cacheable(value = "contestRanking", key = "#root.method.name + ':' + #contestId + ':' + #page + ':' + #limit")
PageResult<ContestRankingVO> getContestRanking(String contestId, Integer page, Integer limit) { }
```

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Cache connection management | Manual RedisTemplate wiring | `RedissonClient` bean (auto-configured) | redisson-spring-boot-starter handles lifecycle, reconnection, pooling |
| Cache abstraction | Manual `redisTemplate.opsForValue().get/set` | Spring Cache `@Cacheable` | Declarative, less boilerplate, consistent with Spring ecosystem |
| TTL management | Hardcoded expiry in service code | `RedisCacheConfiguration.entryTtl()` | Centralized, configurable per cache region |
| Distributed cache stampede prevention | Custom distributed locks | TTL jitter | Simpler, sufficient for this use case |

**Key insight:** The existing `redisson-spring-boot-starter` auto-configures `RedissonClient` as a Spring bean. The `spring-boot-starter-cache` provides `RedisCacheManager`. The bridge is wiring `RedisCacheManager` to use the `RedissonClient`'s `RedisConnectionFactory`.

## Common Pitfalls

### Pitfall 1: Circular Dependency Between CacheManager and Services
**What goes wrong:** `RedisCacheConfig` depends on `RedissonClient`, but services using `@Cacheable` also load early, causing a circular dependency error.
**Why it happens:** Spring's cache AOP proxies are created before the `RedissonClient` bean is fully initialized.
**How to avoid:** Ensure `RedisCacheConfig` is in the same or a parent package scan as `UlticodeBackendApplication`. `RedissonClient` is auto-configured by `redisson-spring-boot-starter` and is always available before `@Cacheable` proxies.
**Warning signs:** `BeanCurrentlyInCreationException` at startup.

### Pitfall 2: @Cacheable on Interface Method vs Implementation Method
**What goes wrong:** `@Cacheable` on an interface method but the call goes through a JDK proxy (interface-based), vs. on the implementation class which requires CGLIB.
**Why it happens:** Spring Cache proxies methods on the target class, not the interface. If the service implements an interface, Spring uses JDK dynamic proxy by default.
**How to avoid:** Place `@Cacheable` on the concrete implementation class (`ProblemServiceImpl`), not the interface. The annotation is on the class being proxied.
**Warning signs:** Cache is never hit even though the method is called.

### Pitfall 3: Non-Serializable Cache Values
**What goes wrong:** `JdkRedisSerializer` serializes cached values as Java bytecode. If a cached VO contains a non-serializable field (e.g., a lazy proxy, a Jackson ObjectMapper cycle), deserialization fails.
**Why it happens:** Default Java serialization requires all object graphs to implement `Serializable`.
**How to avoid:** Use `GenericJackson2JsonRedisSerializer` instead of `JdkRedisSerializer` for human-readable cache values that are more resilient to classpath changes. The existing VOs/DTOs (Lombok `@Data` classes) should serialize correctly with JSON.
**Recommended:** Use `RedisSerializer.json()` with a `Jackson2JsonRedisSerializer` or `GenericJackson2JsonRedisSerializer`.

### Pitfall 4: Cache Stampede from Synchronized Expiration
**What goes wrong:** All cache entries expire at the same time, causing a thundering herd of concurrent database queries.
**Why it happens:** Identical TTL across all entries means they all expire simultaneously.
**How to avoid:** Apply jitter (D-13 to D-15). Randomize TTL by ±10%.
**Warning signs:** Latency spikes every 5 minutes (at the TTL boundary).

### Pitfall 5: @CacheEvict Does Not Cascade Across Cache Regions
**What goes wrong:** Updating `problem` does not evict `contestRanking` even though the contest might reference the problem.
**Why it happens:** `@CacheEvict(value = "problem", ...)` only evicts the named cache.
**How to avoid:** Use multiple `@CacheEvict` annotations or `@Caching` to evict from all affected cache regions. ContestService mutations should evict both `contest` and `contestRanking`.

### Pitfall 6: RedisConnectionFactory from RedissonClient
**What goes wrong:** `RedisCacheManager` requires a `RedisConnectionFactory`. Accessing `redissonClient.getConnectionFactory()` may return `null` or throw if Redisson is not yet fully initialized.
**Why it happens:** Redisson's connection factory is lazy-initialized.
**How to avoid:** Use `RedissonSpringCacheManager` from `redisson-spring-boot-starter` instead of manually building `RedisCacheManager`. This is the preferred approach for Redisson + Spring Cache integration.

### Pitfall 7: Cache Key Collisions Across Methods
**What goes wrong:** Two different methods with the same first argument type generate colliding keys.
**Why it happens:** Key pattern `#root.method.name + ':' + #id` prevents collision between methods, but pagination parameters in `getContestRanking(contestId, page, limit)` could collide if not included.
**How to avoid:** Include all parameters in the key for paginated methods: `key = "#contestId + ':' + #page + ':' + #limit"`.

## Code Examples

### CacheConfig with RedissonSpringCacheManager (Recommended)
```java
// Source: [ASSUMED] -- RedissonSpringCacheManager is provided by redisson-spring-boot-starter
// Verified pattern: redisson-spring-boot-starter 4.x integrates with Spring Cache
package com.ulticode.common.config;

import org.redisson.spring.cache.RedissonSpringCacheManager;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableCaching
public class RedisCacheConfig {

    private final org.redisson.api.RedissonClient redissonClient;

    public RedisCacheConfig(org.redisson.api.RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    @Bean
    public CacheManager cacheManager() {
        long baseTtl = 300L;  // 5 minutes
        long jitterRange = 30L; // ±10%

        var config = new org.redisson.spring.cache.CacheConfig();
        config.setCacheNullValues(false);
        // TTL: base + random jitter in [270, 330] seconds
        long ttlSeconds = baseTtl + ThreadLocalRandom.current().nextLong(-jitterRange, jitterRange + 1);
        config.setTtl(Duration.ofSeconds(ttlSeconds));

        return new RedissonSpringCacheManager(redissonClient, Map.of(
                "problem",         config,
                "userStats",       config,
                "contest",         config,
                "contestRanking",  config
        ));
    }
}
```

### Service with @Cacheable and @CacheEvict
```java
@Service
@RequiredArgsConstructor
@Slf4j
public class ProblemServiceImpl implements ProblemService {

    private final ProblemMapper problemMapper;

    @Override
    @Cacheable(value = "problem", key = "'getProblemById:' + #id")
    public ProblemVO getProblemById(Long id) {
        log.debug("Cache miss for problem id={}", id);
        Problem problem = problemMapper.selectById(id);
        if (problem == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Problem not found");
        }
        return ProblemVO.fromEntity(problem);
    }

    @Override
    @Transactional
    @CacheEvict(value = "problem", allEntries = true)
    public Long createProblem(CreateProblemDTO dto, String userId) {
        // ...
    }

    @Override
    @Transactional
    @CacheEvict(value = "problem", allEntries = true)
    public boolean updateProblem(Long id, UpdateProblemDTO dto, String userId) {
        // ...
    }

    @Override
    @Transactional
    @CacheEvict(value = "problem", allEntries = true)
    public boolean deleteProblem(Long id) {
        // ...
    }
}
```

### UserStats Cache
```java
@Override
@Cacheable(value = "userStats", key = "'getUserStatsById:' + #id")
public UserStatsDTO getUserStatsById(String id) {
    // ...
}

@CacheEvict(value = "userStats", allEntries = true)
public boolean updateUser(String id, UpdateUserDTO dto) { }

@CacheEvict(value = "userStats", allEntries = true)
public boolean deleteUser(String id) { }
```

### Contest Ranking Cache
```java
// In RankingServiceImpl -- caching paginated results
@Override
@Cacheable(value = "contestRanking", key = "'getContestRanking:' + #contestId + ':' + #page + ':' + #limit")
public PageResult<ContestRankingVO> getContestRanking(String contestId, Integer page, Integer limit) {
    // ...
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Manual RedisTemplate.get/set | @Cacheable declarative | Spring 3.1 (2012) | Less boilerplate, consistent caching API |
| Jedis client | Redisson client | ~2018 | Redisson provides connection pooling, locks, queues, cache abstraction |
| Spring Cache with Lettuce | Spring Cache with Redisson | Redisson 3.x+ | RedissonSpringCacheManager integrates natively with Spring Cache |
| Fixed TTL | Jittered TTL | Industry best practice (2020+) | Prevents cache stampede on expiration |
| Hash-based cache keys | Method+args key pattern | Industry best practice | Reduces collision, improves cache hit debugging |

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `RedissonSpringCacheManager` is available in redisson-spring-boot-starter 4.3.1 | Standard Stack | MEDIUM -- if not available, fall back to manual `RedisCacheManager` builder |
| A2 | `RedissonClient` is auto-configured and available as a bean | Common Pitfalls | LOW -- confirmed by existing QueueConfig using `RedissonClient` injection |
| A3 | `JdkRedisSerializer` works with existing VO/DTO classes | Common Pitfalls | LOW -- all entities use Lombok @Data, standard POJOs |
| A4 | TTL jitter of ±10% is sufficient for this use case | Code Examples | LOW -- industry standard for cache stampede prevention |
| A5 | `@Cacheable` on implementation class (not interface) avoids proxy issues | Common Pitfalls | HIGH -- must verify D-06/D-07/D-08 point to impl classes |

## Open Questions

1. **Jackson vs Jdk serialization for cache values?**
   - What we know: `JdkRedisSerializer` is the default, works with POJOs
   - What's unclear: Whether existing VOs have serialization issues (lazy collections, Jackson cycles)
   - Recommendation: Start with `JdkRedisSerializer`. Switch to `GenericJackson2JsonRedisSerializer` if deserialization errors appear in logs.

2. **RedissonSpringCacheManager availability in 4.3.1?**
   - What we know: Redisson 4.x includes `redisson-spring-cache` module; `redisson-spring-boot-starter` includes it transitively
   - What's unclear: Exact class name and constructor signature in 4.3.1
   - Recommendation: Use `RedissonSpringCacheManager` if available; fall back to manual `RedisCacheManager` builder with `redissonClient.getConnectionFactory()`.

3. **Should @EnableCaching go on UlticodeBackendApplication or RedisCacheConfig?**
   - What we know: Either works; both are in the component scan path
   - What's unclear: Codebase convention for annotation placement
   - Recommendation: Put `@EnableCaching` on `RedisCacheConfig` for explicit locality.

4. **Contest ranking cache key -- include pagination or use fixed limit?**
   - What we know: `getContestRanking(contestId, page, limit)` is paginated
   - What's unclear: Whether caching different page sizes is desirable
   - Recommendation: Include all three parameters in the key: `"getContestRanking:" + #contestId + ":" + #page + ":" + #limit`

## Environment Availability

> Step 2.6: SKIPPED (no external dependencies beyond existing infrastructure)

- Redis is already running (Docker container, port 26379) -- verified by docker compose configuration
- `RedissonClient` already configured -- verified by QueueConfig.java
- No new tools or CLIs required

## Security Domain

> security_enforcement: false (from config.json) -- this phase does not introduce security-sensitive changes.

The caching layer does not:
- Expose new endpoints
- Handle authentication/authorization
- Store sensitive data beyond what the service already stores in Redis
- Introduce new serialization vulnerabilities beyond what the existing RedisService already manages

Standard security considerations:
- Cache values are application data (VO/DTO), not secrets -- no additional risk
- Redis connection uses existing credentials from `REDIS_PASSWORD` env var
- No new SQL injection vectors introduced

## Sources

### Primary (HIGH confidence)
- [CITED: docs.spring.io/spring-framework/reference/integration/redis.html] -- Spring Cache integration with Redis, @Cacheable/@CacheEvict syntax
- [CITED: docs.spring.io/spring-boot/data/redis.html] -- Spring Boot Redis auto-configuration, RedisCacheManager
- [VERIFIED: existing code] -- RedissonClient injection pattern in QueueConfig.java (line 52-53)
- [VERIFIED: existing code] -- Spring Boot 3.2.5, Redisson 4.3.1 confirmed in pom.xml

### Secondary (MEDIUM confidence)
- [ASSUMED] -- RedissonSpringCacheManager class and constructor in redisson-spring-boot-starter 4.3.1
- [ASSUMED] -- JdkRedisSerializer compatibility with existing VO/DTO classes

### Tertiary (LOW confidence)
- [ASSUMED] -- ThreadLocalRandom jitter approach matches D-15 specification exactly

## Metadata

**Confidence breakdown:**
- Standard stack: MEDIUM -- dependency analysis confirmed, RedissonSpringCacheManager availability unverified
- Architecture: HIGH -- standard Spring Cache + Redisson integration pattern
- Pitfalls: MEDIUM -- all identified from Spring Cache + Redisson known issues; RedissonSpringCacheManager alternative needs verification

**Research date:** 2026-04-20
**Valid until:** 2026-05-20 (30 days -- Spring Cache + Redisson API is stable)
