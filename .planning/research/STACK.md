# Technology Stack

**Project:** UltiCode v1.5 - Technical Debt Remediation
**Researched:** 2026-04-19

## Recommended Stack

### Core Framework
| Technology | Version | Purpose | Why |
|------------|---------|---------|-----|
| Spring Boot | 3.2.5 | Application framework | Existing - do not upgrade |
| MyBatis-Plus | 3.5.16 | ORM layer | Existing - provides LambdaQueryWrapper, not JPA |
| Redisson | 4.3.1 | Distributed rate limiting & caching | Existing dependency - use instead of adding new lib |

### Rate Limiting
| Technology | Purpose | Why |
|------------|---------|-----|
| Redisson RRateLimiter | Distributed rate limiter | Already in stack, atomic operations, Redis-backed |
| Spring AOP (@Aspect) | Method-level rate limiting | Leverages existing spring-boot-starter-aop |
| Existing @RateLimit annotation | Rate limit configuration | Stub exists at common/annotation/RateLimit.java |

### Redis Caching
| Technology | Purpose | Why |
|------------|---------|-----|
| Spring Cache abstraction | Standard cache API | Works with Redisson as backing provider |
| Redisson RCache | Complex caching (beyond @Cacheable capabilities) | Already in stack, map-backed cache with TTL |
| CacheConstants | Key naming convention | Already exists at infrastructure/redis/CacheConstants.java |

### Database Query Optimization
| Technology | Purpose | Why |
|------------|---------|-----|
| MyBatis XML mappers | Explicit JOIN queries | MyBatis-Plus wrapper doesn't support entity graphs |
| MyBatis-Plus LambdaQueryWrapper | Type-safe query building | Use for simple queries, batch fetch for complex |
| Batch fetch with IN clause | Reduce N+1 for ID lists | Simple pattern compatible with existing codebase |

### Testing & Coverage
| Technology | Purpose | Why |
|------------|---------|-----|
| JUnit 5 | Test framework | Already in spring-boot-starter-test |
| AssertJ | Fluent assertions | Already available |
| Mockito | Mocking | Already available |
| Testcontainers | Integration testing | Already in dependencies |
| JaCoCo | Code coverage | Must add to pom.xml build plugins |

## Alternatives Considered

| Category | Recommended | Alternative | Why Not |
|----------|-------------|-------------|---------|
| Rate Limiting | Redisson AOP | Bucket4j | Redisson already in stack, no new dependency |
| Caching | Spring Cache + Redisson | Caffeine (local only) | Need distributed cache for multi-instance |
| N+1 Fix | XML JOIN queries | jpa-spec or entity graphs | MyBatis-Plus is not JPA - these patterns don't apply |
| Coverage | JaCoCo | Cobertura | JaCoCo is standard for Maven projects, better Sonar integration |

## Installation

```bash
# JaCoCo - add to backend-spring/pom.xml <build><plugins>
# (See ARCHITECTURE.md for full plugin configuration)

# For rate limiting - nothing to install, uses existing Redisson
# RedissonAutoConfiguration is already included via redisson-spring-boot-starter

# For caching - enable in application.yml:
# spring.cache.type=redisson (or generic with RedissonCacheManager)
```

## Sources

- Context7: Redisson 4.3.1 documentation (distributed rate limiting patterns)
- Context7: Spring Boot 3.2 caching documentation
- Context7: MyBatis-Plus 3.5 query optimization
- Official JaCoCo Maven plugin documentation
