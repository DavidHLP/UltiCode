# Phase 22: Redis Caching Layer - Context

**Gathered:** 2026-04-20
**Status:** Ready for planning

<domain>
## Phase Boundary

Read-heavy service operations (problem list, user stats, contest data) cached in Redis with Spring Cache abstraction (@Cacheable/@CacheEvict). Write operations invalidate stale cache entries. TTLs include jitter to prevent cache stampede.
</domain>

<decisions>
## Implementation Decisions

### CACHE-01: Spring Cache Abstraction Layer
- **D-01:** Add `spring-boot-starter-cache` dependency to pom.xml (separate from existing redisson-spring-boot-starter)
- **D-02:** Enable Spring Cache with `@EnableCaching` annotation on a configuration class

### CACHE-02: RedisCacheManager with Redisson Backend
- **D-03:** Configure `RedisCacheManager` (from spring-boot-starter-cache) backed by Redisson in a new `CacheConfig` class or existing `RedisConfig`
- **D-04:** Use Redisson's `RedissonClient` as the cache backend — reuse the RedissonClient bean already configured in the application
- **D-05:** Cache names: `problem`, `userStats`, `contest`, `contestRanking`

### CACHE-03: @Cacheable on Read Operations
- **D-06:** Apply `@Cacheable("problem")` to `ProblemServiceImpl.getProblemById(Long id)` — single problem fetch is read-heavy
- **D-07:** Apply `@Cacheable("userStats")` to `UserServiceImpl.getUserStatsById(String id)` — user statistics are read-heavy
- **D-08:** Apply `@Cacheable("contestRanking")` to `ContestServiceImpl.getContestRanking(Long contestId)` — contest rankings are read-heavy
- **D-09:** Cache key pattern: `#root.methodName + #root.args[0]` (method name + first argument as identifier)

### CACHE-04: @CacheEvict on Mutation Operations
- **D-10:** Apply `@CacheEvict(value = "problem", allEntries = true)` to `ProblemServiceImpl` create/update/delete methods — `createProblem`, `updateProblem`, `deleteProblem`
- **D-11:** Apply `@CacheEvict(value = "userStats", allEntries = true)` to `UserServiceImpl` update/delete methods — `updateUser`, `deleteUser`
- **D-12:** Apply `@CacheEvict(value = {"contestRanking", "contest"}, allEntries = true)` to `ContestServiceImpl` mutation methods — `createContest`, `updateContest`, `deleteContest`, `registerContest`

### CACHE-05: TTL with Jitter
- **D-13:** Base TTL: 5 minutes (300 seconds) for all cache regions
- **D-14:** Jitter: ±10% (270–330 seconds) — random jitter applied on cache write to prevent cache stampede
- **D-15:** Jitter implementation: Use `ThreadLocalRandom` to compute `baseTtl + random.nextInt((int)(baseTtl * 0.1))` at cache write time

### Implementation approach
- **D-16:** Use Spring Cache annotations (`@Cacheable`, `@CacheEvict`) — not manual RedisTemplate operations
- **D-17:** Configure `RedisCacheConfiguration` with `serializeValues()` using Java serialization (default) — already works with existing RedisService pattern
- **D-18:** No need for separate CacheConfig class if RedisConfig already exists — add cache manager bean there

### Claude's Discretion
- Specific Redisson cache settings (maxSize, writeBehind, etc.) — standard defaults acceptable
- Whether to use `@CachePut` for updates instead of evict+re-cache — `@CacheEvict` on mutation is cleaner per success criteria
</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Phase context
- `.planning/ROADMAP.md` § Phase 22 — full phase description, success criteria
- `.planning/REQUIREMENTS.md` § Caching Layer — CACHE-01~05 requirements

### Code references
- `backend-spring/pom.xml` — add spring-boot-starter-cache dependency
- `backend-spring/src/main/java/com/ulticode/modules/problem/service/impl/ProblemServiceImpl.java` — add @Cacheable/@CacheEvict
- `backend-spring/src/main/java/com/ulticode/modules/user/service/impl/UserServiceImpl.java` — add @Cacheable/@CacheEvict
- `backend-spring/src/main/java/com/ulticode/modules/contest/service/impl/ContestServiceImpl.java` — add @Cacheable/@CacheEvict
- `backend-spring/src/main/java/com/ulticode/infrastructure/redis/RedisService.java` — existing Redis operations pattern
- `backend-spring/src/main/java/com/ulticode/modules/queue/config/QueueConfig.java` — existing RedissonClient usage pattern
- `backend-spring/src/main/java/com/ulticode/common/config/` — existing config class location (likely RedisConfig.java)

### Spring Cache + Redisson integration
- Spring Boot `@EnableCaching` annotation
- Spring Cache `@Cacheable`, `@CacheEvict` annotations
- `RedisCacheManager` configuration with Redisson client
- `RedissonClient` Spring bean (already exists)

### No external specs — requirements fully captured in decisions above
</canonical_refs>

<codebase_context>
## Existing Code Insights

### Reusable Assets
- `RedisService.java` — already has structured RedisTemplate operations with error handling
- `RedissonClient` already configured as Spring bean — reuse for cache manager
- Existing service implementations follow `@RequiredArgsConstructor` constructor injection pattern

### Established Patterns
- `@Slf4j` Lombok logging in all service classes
- Constructor injection via `@RequiredArgsConstructor` — all service classes follow this
- Service methods return `VO`/`DTO` objects — cache serialization should handle these

### Integration Points
- `@EnableCaching` goes on main application class or a dedicated `CacheConfig`
- `RedisCacheManager` bean needs to be registered in Spring context
- Cache invalidation hooks into existing create/update/delete service methods
- Redisson 4.3.1 is already in pom.xml — no version change needed
</codebase_context>

<specifics>
## Specific Ideas

No specific external references — standard Spring Cache + Redisson integration approach.
</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope.
</deferred>
