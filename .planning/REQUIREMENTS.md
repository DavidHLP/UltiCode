# Requirements: UltiCode v1.5 Technical Debt Remediation

**Defined:** 2026-04-20
**Core Value:** 平台安全性、功能完整性和交付自动化

## v1 Requirements

### Rate Limiting

- [ ] **RATE-01**: @RateLimit annotation implementation via Redisson AOP aspect with atomic tryAcquire()
- [ ] **RATE-02**: Rate limit applied to all public API endpoints (auth, problem, submission, contest)
- [ ] **RATE-03**: Rate limit returns 429 with Retry-After header when exceeded

### Test Coverage

- [ ] **TEST-01**: JaCoCo Maven plugin added to backend-spring/pom.xml with 50% line / 40% branch thresholds
- [ ] **TEST-02**: JaCoCo exclusions configured for generated mapper classes, entities, DTOs, config classes
- [ ] **TEST-03**: JaCoCo enforcement at verify phase (build fails if threshold not met)

### Security Hardening

- [ ] **SEC-01**: System.out.println in CodeExecutionService replaced with structured logger
- [ ] **SEC-02**: Admin forum stats hardcoded zeros replaced with actual queries against forum_comments and forum_votes tables
- [ ] **SEC-03**: Swagger springdoc incompatibility resolved (springdoc 2.6.0 for Spring Boot 3.2.5)
- [ ] **SEC-04**: CI Flyway download URL updated from obsolete Red Gate URL to current Redgate URL

### Caching Layer

- [ ] **CACHE-01**: Spring Cache enabled with spring-boot-starter-cache dependency
- [ ] **CACHE-02**: RedisCacheManager configured in RedisConfig with Redisson backend
- [ ] **CACHE-03**: @Cacheable applied to read-heavy service methods (problem list, user stats, contest data)
- [ ] **CACHE-04**: @CacheEvict applied to all mutation methods (create/update/delete)
- [ ] **CACHE-05**: Cache TTL configured with jitter to prevent cache stampede

### N+1 Query Optimization

- [ ] **PERF-01**: Contest rankings mapper uses JOIN FETCH to avoid N+1 queries
- [ ] **PERF-02**: Problem list mapper uses JOIN FETCH or batch fetch for tags/difficulty
- [ ] **PERF-03**: Submission list mapper uses JOIN FETCH for problem metadata

### PM2 / Build Infrastructure

- [ ] **INFRA-01**: PM2 ecosystem.config.cjs replaces custom .env parser with dotenv npm package
- [ ] **INFRA-02**: Maven build order documented (recommend-api must mvn install before backend-spring)

### Fragile Code Fixes

- [ ] **FRAG-01**: JWT Token Provider null returns replaced with proper Optional or result logging
- [ ] **FRAG-02**: Redis Service null return points reviewed and handled explicitly by callers
- [ ] **FRAG-03**: Volatile counter in MonitoringServiceImpl replaced with AtomicLong

### Large File Refactoring (Deferred)

- [ ] **REF-01**: ForumServiceImpl split into ForumPostService, ForumCommentService, ForumVoteService
- [ ] **REF-02**: CodeExecutionService extracted into CodeExecutionHelper / SandboxService
- [ ] **REF-03**: ContestServiceImpl extracted into ContestRankingService, ContestSchedulerService

## v2 Requirements (Future)

Deferred from v1.5:

- **CACHE-v2-01**: Cache-aside locking pattern for popular endpoints
- **CACHE-v2-02**: Multi-level cache (local + Redis) for extreme read throughput
- **REF-v2-01**: ModerationServiceImpl refactoring
- **PERF-v2-01**: MyBatis XML mapper batch fetch for all list endpoints

## Out of Scope

Explicitly excluded from v1.5:

| Feature | Reason |
|---------|--------|
| Kubernetes deployment | Docker Compose sufficient for current scale |
| Blue-green/canary deployment | Single VPS, not needed |
| OAuth login expansion | Email/password + existing OAuth sufficient |
| Recommendation service CI/CD | Optional service, low priority |
| Multi-environment (staging+prod) | Single production environment |

## Traceability

| Requirement | Phase | Status |
|-------------|-------|--------|
| RATE-01 | Phase 19 | Pending |
| RATE-02 | Phase 19 | Pending |
| RATE-03 | Phase 19 | Pending |
| TEST-01 | Phase 20 | Pending |
| TEST-02 | Phase 20 | Pending |
| TEST-03 | Phase 20 | Pending |
| SEC-01 | Phase 21 | Pending |
| SEC-02 | Phase 21 | Pending |
| SEC-03 | Phase 21 | Pending |
| SEC-04 | Phase 21 | Pending |
| CACHE-01 | Phase 22 | Pending |
| CACHE-02 | Phase 22 | Pending |
| CACHE-03 | Phase 22 | Pending |
| CACHE-04 | Phase 22 | Pending |
| CACHE-05 | Phase 22 | Pending |
| PERF-01 | Phase 23 | Pending |
| PERF-02 | Phase 23 | Pending |
| PERF-03 | Phase 23 | Pending |
| INFRA-01 | Phase 24 | Pending |
| INFRA-02 | Phase 24 | Pending |
| FRAG-01 | Phase 21 | Pending |
| FRAG-02 | Phase 21 | Pending |
| FRAG-03 | Phase 21 | Pending |
| REF-01 | Phase 25 | Pending |
| REF-02 | Phase 25 | Pending |
| REF-03 | Phase 25 | Pending |

**Coverage:**
- v1 requirements: 25 total
- Mapped to phases: 25
- Unmapped: 0 ✓

---
*Requirements defined: 2026-04-20*
*Last updated: 2026-04-20 during v1.5 initialization*
