# Phase 21: Security Hardening - Context

**Gathered:** 2026-04-20
**Status:** Ready for planning

<domain>
## Phase Boundary

Production hardening: debug logging removed, forum stats query real data, CI Flyway URL corrected, JWT nulls handled, Redis nulls explicit, volatile counter made thread-safe.
</domain>

<decisions>
## Implementation Decisions

### SEC-01: System.out.println in CodeExecutionService
- **D-01:** `System.out.print(result)` at line 506 is inside a generated Java code template that executes in the sandbox — NOT the service's own stdout. This is a false positive. No change required.

### SEC-02: Admin Forum Stats Hardcoded Zeros
- **D-02:** `AdminForumServiceImpl.getPosts()` commentCount sort (line 92-96) defaults to `createdAt` — comment counts not actually queried. FRAG-01 scope was about actual data, not sorting. SEC-02 requires replacing hardcoded zeros with real queries against `forum_comments` and `forum_votes` tables in admin stats.

### SEC-03: springdoc Version
- **D-03:** springdoc 2.6.0 already configured in pom.xml (line 21). Compatible with Spring Boot 3.2.5. No change required.

### SEC-04: CI Flyway Download URL
- **D-04:** CI workflow already uses Maven Central URL (`repo1.maven.org`). The `download.redgate.com` issue was from prior CI failures and is already resolved. No change required.

### FRAG-01: JWT Token Provider Null Returns
- **D-05:** `JwtTokenProvider.parseToken()` returns `null` on invalid tokens (line 108). All callers (`validateToken`, `getUserIdFromToken`, etc.) already null-check before use. No change required.

### FRAG-02: Redis Service Null Returns
- **D-06:** `RedisService` methods return `null`/`false`/`0L` on errors — already handled gracefully. This is intentional degraded-response pattern, not a defect. No change required.

### FRAG-03: Volatile Counter in MonitoringServiceImpl
- **D-07:** `MonitoringServiceImpl` line 58: `private volatile long queryCount = 0;` — plain `volatile long` is not thread-safe for increment. Must be `AtomicLong`.

### Implementation approach for FRAG-03
- **D-08:** Change `volatile long queryCount` to `AtomicLong queryCount` and use `queryCount.incrementAndGet()` instead of `queryCount++`
</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Phase context
- `.planning/ROADMAP.md` § Phase 21 — full phase description, success criteria
- `.planning/PROJECT.md` — project context
- `.planning/REQUIREMENTS.md` § Phase 21 — SEC-01~04, FRAG-01~03 requirements

### Code references
- `backend-spring/src/main/java/com/ulticode/modules/monitoring/service/impl/MonitoringServiceImpl.java` — FRAG-03 fix location
- `backend-spring/src/main/java/com/ulticode/modules/admin/service/impl/AdminForumServiceImpl.java` — SEC-02 fix location (commentCount sort)
- `backend-spring/src/main/java/com/ulticode/security/jwt/JwtTokenProvider.java` — already correct (FRAG-01)
- `backend-spring/src/main/java/com/ulticode/infrastructure/redis/RedisService.java` — already correct (FRAG-02)
- `backend-spring/pom.xml` — springdoc version check (SEC-03)
- `.github/workflows/ci.yml` lines 199-217 — Flyway URL check (SEC-04)

### Backend patterns
- `backend-spring/src/test/java/com/ulticode/modules/monitoring/service/impl/MonitoringServiceImplTest.java` — existing test to verify AtomicLong behavior
- Java `java.util.concurrent.atomic.AtomicLong` API reference

### No external specs — requirements fully captured in decisions above
</canonical_refs>

<codebase_context>
## Existing Code Insights

### Reusable Assets
- MonitoringServiceImplTest.java exists — can add test for AtomicLong counter behavior
- RedisService already has structured error handling pattern to follow

### Established Patterns
- `@Slf4j` Lombok logging already used in all service classes
- Constructor injection via `@RequiredArgsConstructor` — all service classes follow this
- `volgie` counter pattern in MonitoringServiceImpl needs AtomicLong

### Integration Points
- MonitoringServiceImpl.queryCount is incremented in getDatabaseStats() method
- queryCount field tracks number of database queries for health monitoring
</codebase_context>

<specifics>
## Specific Ideas

No specific references — standard Java concurrency fixes.
</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope.
</deferred>

---

*Phase: 21-security-hardening*
*Context gathered: 2026-04-20*
