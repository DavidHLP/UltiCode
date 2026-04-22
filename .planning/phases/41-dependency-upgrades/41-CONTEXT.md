# Phase 41: Dependency Upgrades - Context

**Gathered:** 2026-04-22
**Status:** Ready for planning

<domain>
## Phase Boundary

Update springdoc to 2.8.17 and add Testcontainers Redis dependency to backend-spring/pom.xml. No new functionality — dependency version bumps only.

</domain>

<decisions>
## Implementation Decisions

### Dependency Upgrade Strategy
- **D-01:** springdoc version — BLOCKED: 2.8.17 has ClassNotFoundException (LiteWebJarsResourceResolver) with Spring Boot 3.2.5. springdoc 2.6.0 kept. Upgrade requires Spring Boot 4.x.
- **D-02:** Testcontainers Redis added as test-scoped dependency: com.redis:testcontainers-redis:2.2.2 (version managed separately, not via testcontainers-bom)

### Breaking Changes Verification
- **D-04:** Verify swagger-ui at /swagger-ui.html loads without errors — ✅ Verified (302 redirect)
- **D-05:** Verify /api-docs returns valid OpenAPI JSON — ✅ Verified (OpenAPI 3.0.1, 233 paths)
- **D-06:** springdoc 2.8.17 has ClassNotFoundException (LiteWebJarsResourceResolver) — incompatible with Spring Boot 3.2.5

### Technical Approach
- **D-07:** Backend starts successfully with current springdoc 2.6.0 + testcontainers-redis 2.2.2
- **D-08:** testcontainers-redis from com.redis groupId, not org.testcontainers

### Claude's Discretion
- Dependency groupId/artifactId naming (researcher to verify correct artifact name for Redis testcontainer)
- Whether to use version property for testcontainers-redis or managed BOM version

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Requirements
- `.planning/REQUIREMENTS.md` §DEPS-01 — springdoc 2.6.0 → 2.8.17 upgrade criteria
- `.planning/REQUIREMENTS.md` §DEPS-02 — Testcontainers Redis dependency criteria

### Prior Phase Context
- `.planning/PROJECT.md` — UltiCode project overview, Spring Boot 3.5 stack
- `.planning/STATE.md` — Current milestone v2.0 state

### Backend Conventions
- `.planning/codebase/CONVENTIONS.md` — Java naming and code style conventions
- `backend-spring/pom.xml` — Current dependency versions (springdoc 2.6.0, testcontainers 1.11.3)

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **pom.xml**: Already has testcontainers-bom and testcontainers mysql — pattern to follow for redis testcontainer

### Established Patterns
- **Version properties**: `<springdoc.version>` property already exists in pom.xml — reuse pattern
- **Test scope**: All testcontainers deps are `<scope>test</scope>` — follows existing convention

### Integration Points
- **pom.xml**: Changes are additive (new dependency) or version bumps only — no code changes required
- **Phase 42** (Rate Limiting E2E): Will use Testcontainers Redis that Phase 41 adds

</code_context>

<specifics>
## Specific Ideas

springdoc 2.6.0 was added in Phase 34 to fix compatibility with Spring Boot 3.2.5. This was a known regression. Springdoc 2.8.17 is a newer stable version that maintains Spring Boot 3.x compatibility.

</specifics>

<deferred>
## Deferred Ideas

None — Phase 41 is a pure dependency upgrade with no scope ambiguity.

</deferred>

---

*Phase: 41-dependency-upgrades*
*Context gathered: 2026-04-22*
