# Phase 22: Redis Caching Layer - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-04-20
**Phase:** 22-redis-caching-layer
**Areas discussed:** Spring Cache abstraction, Cache backend, Cached operations, Cache eviction strategy, TTL jitter

---

## Spring Cache Abstraction Layer

| Option | Description | Selected |
|--------|-------------|----------|
| Manual RedisTemplate ops | Use RedisService directly with get/put/delete | |
| Spring Cache annotations | Use @Cacheable/@CacheEvict on service methods | ✓ |

**User's choice:** Spring Cache annotations
**Notes:** Standard Spring Boot pattern, less boilerplate than manual RedisTemplate operations

---

## RedisCacheManager with Redisson Backend

| Option | Description | Selected |
|--------|-------------|----------|
| Separate RedissonCacheManager | Create dedicated cache manager from Redisson client | |
| RedisCacheManager with Redisson | Reuse existing RedissonClient as cache backend via spring-cache abstraction | ✓ |

**User's choice:** RedisCacheManager with Redisson client
**Notes:** Less new code, leverages existing Redisson configuration

---

## Cached Operations

| Option | Description | Selected |
|--------|-------------|----------|
| problem list only | Only cache getProblemList (collection) | |
| getProblemById, getUserStatsById, getContestRanking | Cache all three read-heavy operations | ✓ |

**User's choice:** getProblemById, getUserStatsById, getContestRanking
**Notes:** Standard per-cache-03 requirement

---

## Cache Eviction Strategy

| Option | Description | Selected |
|--------|-------------|----------|
| @CacheEvict on allEntries=true | Evict all entries in cache region on any mutation | ✓ |
| @CachePut for updates | Re-cache on update instead of evict | |

**User's choice:** @CacheEvict on allEntries=true
**Notes:** Cleaner and aligns with success criteria

---

## TTL Jitter

| Option | Description | Selected |
|--------|-------------|----------|
| No jitter | Fixed TTL for all caches | |
| ±10% jitter | Random jitter ±10% to prevent cache stampede | ✓ |

**User's choice:** ±10% jitter
**Notes:** Prevents all cache entries from expiring simultaneously

---

## Claude's Discretion

- Specific Redisson cache settings (maxSize, writeBehind, etc.) — standard defaults acceptable
- Whether to use `@CachePut` for updates instead of evict+re-cache — `@CacheEvict` on mutation is cleaner per success criteria
