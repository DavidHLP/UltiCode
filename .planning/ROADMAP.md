# Roadmap: UltiCode

## Milestones

- ✅ **v1.0 Technical Debt Remediation** — Phases 1-4 (shipped 2026-04-16)
- ✅ **v1.1 Technical Debt Remediation II** — Phases 5-8 (shipped 2026-04-17)
- ✅ **v1.2 CI/CD Pipeline** — Phases 9-11 (shipped 2026-04-18)
- ✅ **v1.3 Core Features** — Phases 12-15 (shipped 2026-04-19)
- ✅ **v1.4 Seed Data Expansion** — Phases 16-18 (shipped 2026-04-19)
- 🔄 **v1.5 Technical Debt Remediation III** — Phases 19-25 (in progress)

## Progress

| Phase | Milestone | Plans Complete | Status |
|-------|-----------|---------------|--------|
| 1-4   | v1.0      | All           | Complete |
| 5-8   | v1.1      | All           | Complete |
| 9-11  | v1.2      | All           | Complete |
| 12-15 | v1.3      | All           | Complete |
| 16-18 | v1.4      | All           | Complete |
| 19    | v1.5      | 1/1           | Complete |
| 20    | v1.5      | 1/1           | In progress |
| 21    | v1.5      | 0/7           | Not started |
| 22    | v1.5      | 0/5           | Not started |
| 23    | v1.5      | 0/3           | Not started |
| 24    | v1.5      | 0/2           | Not started |
| 25    | v1.5      | 0/3           | Not started |

## Phases

- [x] **Phase 19: Rate Limiting Infrastructure** — Redisson AOP rate limiting on all public endpoints (completed 2026-04-19)
- [x] **Phase 20: JaCoCo Coverage Baseline** — Maven coverage enforcement at 50% line / 40% branch
- [ ] **Phase 21: Security Hardening** — Logging fixes, forum stats, springdoc version, CI URL
- [ ] **Phase 22: Redis Caching Layer** — Spring Cache with @Cacheable/@CacheEvict on service methods
- [ ] **Phase 23: N+1 Query Optimization** — JOIN FETCH in contest, problem, and submission mappers
- [ ] **Phase 24: PM2 / Build Infrastructure** — dotenv package, Maven build order documentation
- [ ] **Phase 25: Large File Refactoring** — ForumServiceImpl, CodeExecutionService, ContestServiceImpl split

---

## Phase Details

### Phase 19: Rate Limiting Infrastructure

**Goal**: Public API endpoints are protected by distributed rate limiting using Redisson atomic operations

**Depends on**: Nothing (first v1.5 phase)

**Requirements**: RATE-01, RATE-02, RATE-03

**Success Criteria** (what must be TRUE):
1. User hitting any public API endpoint triggers @RateLimit aspect with atomic tryAcquire()
2. User exceeding rate limit receives HTTP 429 with Retry-After header
3. Rate limiting applies to auth, problem, submission, and contest endpoint groups

**Plans**: 1 plan

Plans:
- [x] 19-01-PLAN.md — Add Retry-After header to HTTP 429 responses for rate limit errors

### Phase 20: JaCoCo Coverage Baseline

**Goal**: Backend build enforces minimum test coverage to prevent regression

**Depends on**: Phase 19

**Requirements**: TEST-01, TEST-02, TEST-03

**Success Criteria** (what must be TRUE):
1. `mvn verify` fails when line coverage drops below 50%
2. `mvn verify` fails when branch coverage drops below 40%
3. JaCoCo report excludes generated mapper classes, entities, DTOs, and config classes

**Plans**: 1 plan

Plans:
- [x] 20-01-PLAN.md — Configure JaCoCo coverage enforcement in Maven build

### Phase 21: Security Hardening

**Goal**: Production systems do not leak information via debug patterns or hardcoded values

**Depends on**: Phase 20

**Requirements**: SEC-01, SEC-02, SEC-03, SEC-04, FRAG-01, FRAG-02, FRAG-03

**Success Criteria** (what must be TRUE):
1. CodeExecutionService uses structured logger instead of System.out.println
2. Admin forum stats page displays actual comment/vote counts from database
3. Swagger UI loads correctly with springdoc 2.6.0 on Spring Boot 3.2.5
4. CI Flyway migration uses current Redgate URL (not obsolete Red Gate URL)
5. JWT Token Provider handles null returns with Optional or logged warnings
6. Redis Service null returns are explicitly handled by callers
7. MonitoringServiceImpl volatile counter uses AtomicLong for thread safety

**Plans**: 1 plan

Plans:
- [ ] 19-01-PLAN.md — Add Retry-After header to HTTP 429 responses for rate limit errors

### Phase 22: Redis Caching Layer

**Goal**: Read-heavy service operations are cached in Redis with proper invalidation

**Depends on**: Phase 19 (rate limiting must be in place first)

**Requirements**: CACHE-01, CACHE-02, CACHE-03, CACHE-04, CACHE-05

**Success Criteria** (what must be TRUE):
1. Spring Cache abstraction is enabled with spring-boot-starter-cache dependency
2. RedisCacheManager is configured with Redisson backend in RedisConfig
3. Problem list, user stats, and contest data queries use @Cacheable
4. All create/update/delete operations use @CacheEvict to invalidate stale entries
5. Cache TTLs include jitter to prevent cache stampede on expiration

**Plans**: 1 plan

Plans:
- [ ] 19-01-PLAN.md — Add Retry-After header to HTTP 429 responses for rate limit errors

### Phase 23: N+1 Query Optimization

**Goal**: List queries use JOIN FETCH to avoid N+1 database roundtrips

**Depends on**: Phase 22

**Requirements**: PERF-01, PERF-02, PERF-03

**Success Criteria** (what must be TRUE):
1. Contest rankings page loads all participants in single query with JOIN FETCH
2. Problem list page loads tags and difficulty in single query (not N+1 lazy loads)
3. Submission list page loads problem metadata in single query with JOIN FETCH

**Plans**: 1 plan

Plans:
- [ ] 19-01-PLAN.md — Add Retry-After header to HTTP 429 responses for rate limit errors

### Phase 24: PM2 / Build Infrastructure

**Goal**: Build system uses standard tooling for environment management

**Depends on**: Phase 23

**Requirements**: INFRA-01, INFRA-02

**Success Criteria** (what must be TRUE):
1. PM2 ecosystem.config.cjs uses dotenv npm package instead of custom .env parser
2. Build documentation specifies recommend-api must `mvn install` before backend-spring

**Plans**: 1 plan

Plans:
- [ ] 19-01-PLAN.md — Add Retry-After header to HTTP 429 responses for rate limit errors

### Phase 25: Large File Refactoring

**Goal**: Monolithic service classes are split into focused domain modules

**Depends on**: Phase 24 (requires coverage baseline to safely refactor)

**Requirements**: REF-01, REF-02, REF-03

**Success Criteria** (what must be TRUE):
1. ForumServiceImpl is split into ForumPostService, ForumCommentService, ForumVoteService
2. CodeExecutionService extraction produces CodeExecutionHelper or SandboxService
3. ContestServiceImpl is split into ContestRankingService, ContestSchedulerService

**Plans**: 1 plan

Plans:
- [ ] 19-01-PLAN.md — Add Retry-After header to HTTP 429 responses for rate limit errors

---

## v1.5 Coverage

| Phase | Requirements | Success Criteria |
|-------|--------------|------------------|
| 19 - Rate Limiting | RATE-01, RATE-02, RATE-03 | 3 |
| 20 - JaCoCo | TEST-01, TEST-02, TEST-03 | 3 |
| 21 - Security | SEC-01~04, FRAG-01~03 | 7 |
| 22 - Caching | CACHE-01~05 | 5 |
| 23 - N+1 Opt | PERF-01~03 | 3 |
| 24 - Infra | INFRA-01, INFRA-02 | 2 |
| 25 - Refactor | REF-01~03 | 3 |

**Total**: 25/25 requirements mapped ✓
