# Phase 43: JaCoCo Threshold Raise - Context

**Gathered:** 2026-04-22
**Status:** Ready for planning

<domain>
## Phase Boundary

Raise JaCoCo coverage thresholds in pom.xml: LINE 3%→5%, BRANCH 1%→3%. Depends on Phase 42 tests adding coverage.

</domain>

<decisions>
## Implementation Decisions

### JaCoCo Thresholds
- **D-01:** pom.xml `jacoco-maven-plugin` LINE minimum updated from `0.03` (3%) to `0.05` (5%)
- **D-02:** pom.xml `jacoco-maven-plugin` BRANCH minimum updated from `0.01` (1%) to `0.03` (3%)
- **D-03:** Thresholds raised after Phase 42 E2E tests added coverage — JAC-01 prerequisite satisfied

### Verification Approach
- **D-04:** Run `mvn verify` locally to confirm coverage exceeds new thresholds before CI
- **D-05:** Build fails if coverage report shows LINE < 5% or BRANCH < 3%

### No Gray Areas
Phase 43 is a pure configuration value change with no ambiguity. Success criteria from JAC-01 are exact:
1. pom.xml JaCoCo LINE threshold updated from 3% to 5% ✓
2. pom.xml JaCoCo BRANCH threshold updated from 1% to 3% ✓
3. `mvn verify` fails if coverage falls below thresholds ✓
4. CI build passes with new thresholds (Phase 42 tests add coverage) ✓

No discussion needed — proceed to planning.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Requirements
- `.planning/REQUIREMENTS.md` §JAC-01 — JaCoCo thresholds raise criteria (LINE 3%→5%, BRANCH 1%→3%)

### Prior Phase Context
- `.planning/phases/42-rate-limiting-e2e-tests/42-CONTEXT.md` — Rate Limiting E2E tests (Phase 42) — provides coverage that enables threshold raise
- `.planning/phases/41-dependency-upgrades/41-CONTEXT.md` — Testcontainers Redis added (Phase 41)

### Backend Conventions
- `.planning/codebase/CONVENTIONS.md` — Java naming and code style conventions
- `backend-spring/pom.xml` — Current JaCoCo configuration (LINE 3%, BRANCH 1%, lines 265-324)

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **pom.xml jacoco-maven-plugin**: Already configured with prepare-agent, report, check executions — only threshold values change

### Established Patterns
- **JaCoCo threshold format**: `<minimum>0.03</minimum>` expressed as decimal ratio (0.03 = 3%)
- **Verification binding**: `jacoco:check` bound to `verify` phase (confirmed in Phase 40)

### Integration Points
- **pom.xml line 287-296**: Rule definitions with LINE and BRANCH counters — only `<minimum>` values change
- **CI verification**: `mvn verify` → `jacoco:check` → build passes/fails based on coverage

</code_context>

<specifics>
## Specific Ideas

JaCoCo thresholds were lowered to unblock CI in Phase 40 (LINE 3%, BRANCH 1%). Phase 42 E2E tests add coverage that justifies raising thresholds. This is the natural next step in the coverage improvement roadmap.

</specifics>

<deferred>
## Deferred Ideas

None — Phase 43 scope is a single focused change with no ambiguity.

</deferred>

---

*Phase: 43-jacoco-threshold-raise*
*Context gathered: 2026-04-22*
