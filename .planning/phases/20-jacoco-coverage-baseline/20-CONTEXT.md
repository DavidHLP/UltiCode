# Phase 20: JaCoCo Coverage Baseline - Context

**Gathered:** 2026-04-20
**Status:** Ready for planning

<domain>
## Phase Boundary

JaCoCo coverage enforcement added to backend Maven build. `mvn verify` fails when line coverage < 50% or branch coverage < 40%. Coverage excludes generated mapper classes, entities, DTOs, and config classes.
</domain>

<decisions>
## Implementation Decisions

### Coverage thresholds
- **D-01:** Line coverage minimum: 50%
- **D-02:** Branch coverage minimum: 40%

### Exclusion patterns
- **D-03:** Generated MyBatis-Plus mapper classes excluded (e.g., `*Mapper.xml`, `*Mapper.java` auto-generated)
- **D-04:** Entity classes excluded (e.g., `com.ulticode.modules.*.entity.*`)
- **D-05:** DTO and response classes excluded (e.g., `com.ulticode.modules.*.dto.*`, `com.ulticode.modules.*.response.*`)
- **D-06:** Configuration classes excluded (e.g., `*Config.java`, `*Properties.java`)
- **D-07:** `pom.xml` excluded from coverage

### Enforcement mechanism
- **D-08:** JaCoCo enforcement via `mvn verify` (not `mvn test`) — allows integration tests in verify phase
- **D-09:** JaCoCo report generated at `target/site/jacoco/index.html`
- **D-10:** Build fails (not warns) when coverage thresholds not met

### Test infrastructure
- **D-11:** Use existing Testcontainers setup from Phase 8/9 (141 tests already in place)
- **D-12:** Surefire for unit tests, Failsafe for integration tests (standard Maven pattern)

### Claude's Discretion
- JaCoCo agent configuration (jvm args, output format) — standard Maven defaults acceptable
- Specific exclude patterns for generated code — based on standard MyBatis-Plus conventions
</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Phase context
- `.planning/ROADMAP.md` § Phase 20 — full phase description, success criteria
- `.planning/PROJECT.md` — project context, testing principles
- `.planning/PHASE-19-CONTEXT.md` (if exists) — rate limiting context for dependency ordering

### Backend test patterns
- `backend-spring/src/test/java/com/ulticode/` — existing 110 test files
- `backend-spring/pom.xml` — current Maven configuration

### No external specs — requirements fully captured in decisions above
</canonical_refs>

<codebase_context>
## Existing Code Insights

### Reusable Assets
- Existing 110+ test files in `backend-spring/src/test/` — no need to create new tests, just enforce coverage
- Testcontainers BOM already configured in pom.xml — integration test infrastructure ready
- Recommendation module already has JaCoCo site output at `recommend-core/target/site/jacoco/` — reference for how JaCoCo works in this project

### Established Patterns
- Spring Boot test: `@SpringBootTest` avoided in favor of `@WebMvcTest` and Testcontainers for isolation
- Integration tests use Testcontainers MySQL and Redis

### Integration Points
- JaCoCo agent injected via Maven `argLine` parameter in pom.xml
- Coverage report generated to `target/site/jacoco/`
- Enforcement happens at `verify` lifecycle phase
</codebase_context>

<specifics>
## Specific Ideas

No specific references — standard JaCoCo Maven enforcement approach.
</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope.
</deferred>

---

*Phase: 20-jacoco-coverage-baseline*
*Context gathered: 2026-04-20*
