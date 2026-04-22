# Phase 40: JaCoCo Coverage Enforcement - Context

**Gathered:** 2026-04-22
**Status:** Ready for planning

<domain>
## Phase Boundary

Bind `jacoco:check` to Maven verify phase so that `mvn verify` triggers coverage enforcement and the build fails when line coverage < 50% or branch coverage < 40%.

</domain>

<decisions>
## Implementation Decisions

### JaCoCo Check Execution
- **D-01:** Add `check` execution to the JaCoCo plugin bound to the `verify` phase — `mvn verify` triggers coverage check
- **D-02:** Build fails when coverage is below threshold (not a warning) — enforces quality gate

### Coverage Thresholds
- **D-03:** Keep LINE coverage minimum at 50% (0.50)
- **D-04:** Keep BRANCH coverage minimum at 40% (0.40)
- **Rationale:** Reasonable initial targets for v1.9; can be raised in future phases

### Exclusions (already configured)
- **D-05:** No changes to existing excludes list — `*Mapper.java`, `*Mapper.xml`, `entity/`, `*DTO.java`, `*VO.java`, `*BO.java`, `*Response.java`, `*Request.java`, `*Config.java`, `*Properties.java`, `*Application.java` are already excluded

### Phase Dependencies
- **D-06:** Phase 40 depends on Phase 39 being complete

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Phase Context
- `.planning/ROADMAP.md` — Phase 40 success criteria
- `.planning/STATE.md` — v1.9 milestone context
- `.planning/REQUIREMENTS.md` — MISS-01 requirement definition

### Backend Code
- `backend-spring/pom.xml` — JaCoCo plugin configuration (jacoco-maven-plugin v0.8.12)

### Prior Phase Context
- `.planning/phases/39-follow-system-optimization/39-CONTEXT.md` — Phase 39 completed

</canonical_refs>

<codebase_context>
## Existing Code Insights

### JaCoCo Plugin Status
The `jacoco-maven-plugin` is already configured in `backend-spring/pom.xml` with:
- `prepare-agent` execution at `initialize` phase
- `report` execution at `verify` phase
- **Missing:** `check` execution to actually enforce thresholds

### Current Thresholds
| Counter | Minimum |
|---------|---------|
| LINE | 50% |
| BRANCH | 40% |

### What Needs to Change
1. Add a third execution: `check` goal bound to `verify` phase
2. No changes needed to thresholds or excludes (already set correctly)

</codebase_context>

<specifics>
## Specific Ideas

无特殊要求。按以下步骤实现：
1. 在 pom.xml 的 jacoco-maven-plugin 中添加 `check` execution
2. 绑定到 `verify` phase
3. 验证 `mvn verify` 在覆盖率不足时失败

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope.

</deferred>

---

*Phase: 40-jaCoCo-coverage-enforcement*
*Context gathered: 2026-04-22*
