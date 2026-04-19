# Project Research Summary

**Project:** UltiCode v1.5 Technical Debt Remediation
**Domain:** Online Judge Platform — Spring Boot Backend Remediation
**Researched:** 2026-04-20
**Confidence:** MEDIUM-HIGH

## Executive Summary

UltiCode v1.5 addresses critical infrastructure gaps in an existing Spring Boot 3.2.5 + MyBatis-Plus online judge platform. The remediation targets 23 issues across 7 categories, with the highest priority being distributed rate limiting and code coverage enforcement. Research indicates the codebase has proper foundations (Redisson already in stack, annotation stubs exist) but lacks implementations.

The recommended approach follows a dependency-ordered sequence: rate limiting first (protects infrastructure), then JaCoCo (establishes baseline), then security fixes, caching layer, and finally N+1 query optimization. Key risks include race conditions in rate limiter implementation (must use atomic operations), cache stampede on miss (requires jittered TTLs), and coverage gates blocking all work (start at 50%, not 80%).

This is a technical debt remediation project, not a new feature launch. The goal is to harden production infrastructure with tested patterns before adding new functionality.

## Key Findings

### Recommended Stack

**Core framework:** Spring Boot 3.2.5 (existing, do not upgrade), MyBatis-Plus 3.5.16, Redisson 4.3.1. All rate limiting and caching leverages Redisson which is already a dependency. No new external services required.

**Core technologies:**
- Redisson RRateLimiter — distributed rate limiting via atomic Lua scripts
- Spring Cache + RedissonCacheManager — standard caching abstraction with Redis backend
- JaCoCo 0.8.11+ Maven plugin — coverage enforcement with threshold gates
- MyBatis XML mappers — explicit JOIN queries to avoid N+1 (MyBatis-Plus wrapper alone insufficient)

### Expected Features

**Must have (table stakes for production):**
- Rate limiting on all public endpoints — 429 response with Retry-After header when exceeded
- JaCoCo coverage enforcement at 50% line / 40% branch minimum
- Spring Cache configured with Redis backend for read-heavy queries

**Should have (reliability improvements):**
- N+1 query fixes via JOIN FETCH in contest rankings, problem lists, submission queries
- System.out.println removal in favor of structured logging
- Hardcoded forum statistics replaced with actual queries

**Defer (v2+):**
- Large file refactoring (ForumServiceImpl, CodeExecutionService, ContestServiceImpl)
- Advanced caching patterns (cache-aside locking, multi-level)

### Architecture Approach

The architecture follows standard Spring Boot AOP patterns for cross-cutting concerns. RateLimitAspect intercepts @RateLimit annotations using @Around advice, acquiring permits atomically from Redisson before allowing request to proceed. Cache operations sit at the service layer (not controller) to keep cache keys domain-aligned and invalidation straightforward.

**Major components:**
1. RateLimitAspect — AOP aspect using Redisson RRateLimiter for distributed rate limiting
2. RedisCacheConfig — RedissonCacheManager with per-cache TTL configuration
3. JaCoCo Maven plugin — bytecode instrumentation for coverage reporting and gates
4. MyBatis XML mappers — explicit JOIN queries replacing lazy-loading N+1 patterns

### Critical Pitfalls

1. **Race condition in rate limiter** — Non-atomic tryAcquire causes burst bypass under load. Must use Redisson's atomic tryAcquire() with no check-then-act separation.

2. **Cache stampede on miss** — Multiple simultaneous requests hit DB when cache expires. Prevention: jittered TTLs or cache-aside locking pattern.

3. **MyBatis-Plus N+1 in list queries** — Accessing relations in loops triggers O(n) queries. Prevention: EXPLAIN ANALYZE every list endpoint; use JOIN FETCH in XML mappers.

4. **JaCoCo coverage gate blocking all work** — Setting 80% threshold immediately fails build on legacy codebase. Prevention: Start at 50% line coverage, increment gradually.

5. **Cache invalidation missing on updates** — @CacheEvict required on all mutation methods or stale data persists for full TTL.

## Implications for Roadmap

Based on research, the following phase structure addresses dependencies and avoids critical pitfalls:

### Phase 1: Rate Limiting Infrastructure
**Rationale:** Must come first — protects all downstream infrastructure from abuse. Annotation stub exists but implementation missing.
**Delivers:** RateLimitAspect with Redisson RRateLimiter, @RateLimit on all public endpoints, 429 responses with Retry-After header.
**Addresses:** MISS-01 (annotation exists, impl missing)
**Avoids:** Race condition pitfall — atomic tryAcquire only

### Phase 2: JaCoCo Coverage Baseline
**Rationale:** Establishes baseline before adding new code; low complexity, no external dependencies.
**Delivers:** jacoco-maven-plugin in pom.xml, 50% line / 40% branch thresholds, exclusions configured.
**Addresses:** MISS-02 (tests exist, no enforcement)
**Avoids:** Coverage gate pitfall — start at 50%, not 80%

### Phase 3: Security Hardening
**Rationale:** Removes information leakage and fragile debug patterns. Independent of other phases.
**Delivers:** System.out.println replaced with structured logging, forum stats returning actual counts.
**Addresses:** HARD-01, HARD-02, SCALE-02

### Phase 4: Redis Caching Layer
**Rationale:** Protects database from read-heavy queries; requires rate limiting in place first to prevent cache abuse.
**Delivers:** @Cacheable on problem/user/contest queries, @CacheEvict on mutations, RedisCacheManager configured.
**Addresses:** SCALE-01, MISS-03
**Avoids:** Cache stampede pitfall (jittered TTLs), invalidation pitfall (@CacheEvict on all mutations)

### Phase 5: N+1 Query Optimization
**Rationale:** Requires careful mapper XML changes; best done after caching layer is tested.
**Delivers:** JOIN FETCH in contest rankings, problem lists, submission queries; EXPLAIN ANALYZE verification.
**Addresses:** PERF-01
**Avoids:** Breaking existing queries — add mapper tests before changes

### Phase 6: Large File Refactoring
**Rationale:** LOW priority; dependent on coverage baseline to safely refactor.
**Delivers:** Split ForumServiceImpl, CodeExecutionService, ContestServiceImpl into smaller modules.

### Phase Ordering Rationale

- Rate limiting must precede caching (rate limit protects cache layer)
- JaCoCo should be added early (establishes baseline before code changes)
- N+1 fixes require mapper XML changes (coordinate with feature work to avoid conflicts)
- Caching should be added after rate limiting to protect cache infrastructure
- Large file refactoring deferred until coverage baseline established

### Research Flags

Phases likely needing deeper research during planning:
- **Phase 4 (Caching):** May need additional research on RedissonCacheManager TTL jittering for cache stampede prevention
- **Phase 5 (N+1):** Contest ranking query optimization may need MyBatis XML examples specific to existing mapper structure

Phases with standard patterns (skip research-phase):
- **Phase 1 (Rate Limiting):** Redisson AOP patterns well-documented
- **Phase 2 (JaCoCo):** Maven plugin configuration is standard
- **Phase 3 (Security):** Logging replacement is straightforward

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | HIGH | Uses existing dependencies; no new external services |
| Features | HIGH | Based on CONCERNS.md analysis of actual codebase |
| Architecture | MEDIUM | Spring AOP + Redisson patterns well-documented; specific TTL values may need tuning |
| Pitfalls | HIGH | Based on established best practices and common Spring/Redisson mistakes |

**Overall confidence:** MEDIUM-HIGH

### Gaps to Address

- **JaCoCo current baseline:** Unknown current coverage percentage. Phase 2 should measure baseline first before setting thresholds.
- **Specific N+1 queries:** Only contest mappers identified. Full audit of all list queries needed during Phase 5 planning.
- **Cache invalidation scope:** Not all mutation methods identified. Full audit of create/update/delete needed during Phase 4.

## Sources

### Primary (HIGH confidence)
- CONCERNS.md — v1.5 backlog, 23 issues with categories and priority
- RateLimitAspect.java — existing implementation stub
- RedisService.java — existing Redis operations
- pom.xml — existing dependencies

### Secondary (MEDIUM confidence)
- Context7: Spring Boot 3.2 AOP documentation — rate limiting aspect patterns
- Context7: Redisson 4.3.1 documentation — RRateLimiter atomic operations
- Context7: Spring Cache with Redisson — CacheManager configuration

### Tertiary (LOW confidence)
- MyBatis-Plus N+1 patterns — inferred from MyBatis behavior; specific mapper changes need verification

---

*Research completed: 2026-04-20*
*Ready for roadmap: yes*
