# Phase 44: Testcontainers Upgrade - Context

**Gathered:** 2026-04-22
**Status:** Ready for planning

<domain>
## Phase Boundary

Upgrade testcontainers-bom and testcontainers from 1.11.3 to latest stable 11.x. Fix any API breaking changes.

</domain>

<decisions>
## Implementation Decisions

### Version Target
- **D-01:** Upgrade to testcontainers 1.21.4 — last stable 1.x release (11.x is 2.x naming scheme incompatible)

### Dependency Updates
- **D-02:** testcontainers-bom: 1.11.3 → latest stable 11.x
- **D-03:** testcontainers: 1.11.3 → latest stable 11.x
- **D-04:** junit-jupiter: 1.11.3 → latest stable 11.x
- **D-05:** mysql: 1.11.3 → latest stable 11.x
- **D-06:** testcontainers-redis: 2.2.2 — keep existing (maintained separately, not under testcontainers BOM)

### API Compatibility
- **D-07:** GenericContainer API — check for breaking changes in 11.x vs 1.11.3
- **D-08:** @Testcontainers, @Container, @DynamicPropertySource — verify compatibility
- **D-09:** If breaking changes found, update RateLimitIntegrationTest.java accordingly

### Verification
- **D-10:** Run `mvn test` to confirm existing Testcontainers-based tests pass after upgrade
- **D-11:** RateLimitIntegrationTest.java serves as the primary verification test

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Requirements
- `.planning/REQUIREMENTS.md` — DEPS-02 related (Testcontainers Redis added in Phase 41)

### Prior Phase Context
- `.planning/phases/42-rate-limiting-e2e-tests/42-CONTEXT.md` — Rate Limiting E2E tests using testcontainers
- `.planning/phases/43-jacoco-threshold-raise/43-CONTEXT.md` — JaCoCo threshold raise

### Backend Conventions
- `.planning/codebase/CONVENTIONS.md` — Java naming and code style conventions
- `backend-spring/pom.xml` — Current testcontainers dependencies (lines 30-31, 182-201)
- `backend-spring/src/test/java/com/ulticode/modules/auth/controller/RateLimitIntegrationTest.java` — Existing testcontainers usage

</canonical_refs>

<codebase_context>
## Existing Code Insights

### Reusable Assets
- **RateLimitIntegrationTest.java**: Uses GenericContainer, @Container, @Testcontainers, @DynamicPropertySource — upgrade must maintain this pattern
- **pom.xml**: testcontainers-bom manages versions for testcontainers, junit-jupiter, mysql

### Established Patterns
- **Static container**: `@Container static GenericContainer<>` pattern — verify this works in 11.x
- **Dynamic properties**: @DynamicPropertySource for overriding Redis host/port — standard Spring Boot + Testcontainers pattern

### Integration Points
- **Redis testcontainer**: testcontainers-redis 2.2.2 is NOT under the testcontainers BOM — managed separately
- **MySQL testcontainer**: testcontainers-mysql managed under testcontainers BOM — version synced with BOM

</codebase_context>

<specifics>
## Specific Ideas

Upgrade all testcontainers BOM-managed dependencies to latest stable 11.x. Keep testcontainers-redis at 2.2.2 (separate maintenance). Check for breaking changes in GenericContainer API between 1.11.3 and 11.x. Primary verification: run existing tests.

</specifics>

<deferred>
## Deferred Ideas

None — Phase scope is clear and focused on upgrade.

</deferred>

---

*Phase: 44-testcontainers-upgrade*
*Context gathered: 2026-04-22*
