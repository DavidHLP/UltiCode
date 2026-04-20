# Phase 25: Large File Refactoring - Context

**Gathered:** 2026-04-20
**Status:** Ready for planning

<domain>
## Phase Boundary

Monolithic service classes are split into focused domain modules. Three targets:
- **REF-01**: ForumServiceImpl (693 lines) → ForumPostService, ForumCommentService, ForumVoteService
- **REF-02**: CodeExecutionService (643 lines) → CodeExecutionHelper + SandboxService
- **REF-03**: ContestServiceImpl (633 lines) → ContestRankingService + ContestSchedulerService

**Depends on:** Phase 24 (requires coverage baseline to safely refactor)

</domain>

<decisions>
## Implementation Decisions

### REF-01: ForumServiceImpl Split

- **D-01:** Keep `ForumService` interface + `ForumServiceImpl` as a **facade** that delegates to three new focused services
- **D-02:** Create three new service interfaces: `ForumPostService`, `ForumCommentService`, `ForumVoteService`
- **D-03:** Create three new implementation classes: `ForumPostServiceImpl`, `ForumCommentServiceImpl`, `ForumVoteServiceImpl`
- **D-04:** Each new service gets its own mapper dependency injected via constructor
- **D-05:** Methods in `ForumServiceImpl` are redistributed by primary entity: post methods → ForumPostService, comment methods → ForumCommentService, vote methods → ForumVoteService
- **D-06:** Shared utility methods (e.g., time formatting, page wrapping) stay in `ForumServiceImpl` facade or move to a `ForumHelper`
- **D-07:** Controller (`ForumController`) stays unchanged — it already depends only on `ForumService`

### REF-02: CodeExecutionService Split

- **D-08:** Extract **sandbox security operations** (cgroup, seccomp, process launching) into `SandboxService` — the `@Resource` / `Exec` subprocess handling
- **D-09:** Extract **language-specific result parsing and execution helpers** into `CodeExecutionHelper` — the per-language compile/run logic
- **D-10:** `CodeExecutionService` becomes a **thin facade** that orchestrates SandboxService + CodeExecutionHelper
- **D-11:** `SandboxService` is a `@Service` with `@Resource ProcessBuilder` or `Exec` wrapper, handles security constraints
- **D-12:** `CodeExecutionHelper` is a component/helper class (not a full service) — stateless, per-language logic
- **D-13:** Language-specific classes (PythonExecutor, JavaExecutor, etc.) are candidates to move into CodeExecutionHelper package

### REF-03: ContestServiceImpl Split

- **D-14:** ContestRankingService already exists (`RankingServiceImpl`). Extract any remaining ranking-related logic from ContestServiceImpl into `RankingServiceImpl` (or `ContestRankingService` if rename preferred)
- **D-15:** Extract **scheduling/time-driven operations** (start/end time checks, auto-status updates, countdown logic) into `ContestSchedulerService`
- **D-16:** `ContestSchedulerService` is a `@Service` that may be called by ContestScheduler or a `@Scheduled` method
- **D-17:** `ContestServiceImpl` becomes a **facade** orchestrating RankingService + ContestSchedulerService + remaining contest logic

### General Refactoring Principles

- **D-18:** Use constructor injection for all service dependencies (per Java rules)
- **D-19:** Each extracted service class target: ≤250 lines (Java convention)
- **D-20:** No new public APIs — just internal decomposition; controller interfaces remain unchanged
- **D-21:** Run `./mvnw test` after each split to verify no regressions
- **D-22:** If JaCoCo coverage exists from Phase 20, verify coverage remains above baseline after each split

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Phase requirements
- `.planning/REQUIREMENTS.md` §REF-01 — ForumServiceImpl split
- `.planning/REQUIREMENTS.md` §REF-02 — CodeExecutionService extraction
- `.planning/REQUIREMENTS.md` §REF-03 — ContestServiceImpl extraction
- `.planning/ROADMAP.md` — Phase 25 success criteria

### Existing code (primary targets)
- `backend-spring/src/main/java/com/ulticode/modules/forum/service/impl/ForumServiceImpl.java` — 693 lines, needs split
- `backend-spring/src/main/java/com/ulticode/modules/forum/service/ForumService.java` — existing interface (line 1)
- `backend-spring/src/main/java/com/ulticode/modules/submission/service/CodeExecutionService.java` — 643 lines, needs split
- `backend-spring/src/main/java/com/ulticode/modules/contest/service/impl/ContestServiceImpl.java` — 633 lines, needs split

### Existing related services
- `backend-spring/src/main/java/com/ulticode/modules/contest/service/impl/RankingServiceImpl.java` — existing ranking logic
- `backend-spring/src/main/java/com/ulticode/modules/contest/service/impl/RatingCalculationServiceImpl.java` — existing rating logic
- `backend-spring/src/main/java/com/ulticode/modules/contest/scheduler/ContestScheduler.java` — existing scheduler

### Forum module mappers (for new service injection)
- `backend-spring/src/main/java/com/ulticode/modules/forum/mapper/ForumPostMapper.java`
- `backend-spring/src/main/java/com/ulticode/modules/forum/mapper/ForumCommentMapper.java`
- `backend-spring/src/main/java/com/ulticode/modules/forum/mapper/ForumVoteMapper.java`

### Java rules
- `~/.claude/rules/java/coding-style.md` — constructor injection, records, modern Java features
- `~/.claude/rules/java/patterns.md` — service layer patterns, repository pattern
- `~/.claude/rules/java/testing.md` — JUnit 5, AssertJ, 80% coverage target

### Prior phase context
- `.planning/phases/23-n-1-query-optimization/23-CONTEXT.md` — JOIN FETCH decisions
- `.planning/phases/20-jacoco-coverage-baseline/20-CONTEXT.md` — JaCoCo coverage baseline

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `RankingServiceImpl`, `RatingCalculationServiceImpl` — already extracted from ContestServiceImpl, serve as pattern reference
- `ContestScheduler.java` — existing scheduler, boundary point for scheduling logic
- Forum module mappers (ForumPostMapper, ForumCommentMapper, ForumVoteMapper) — ready for injection into new services

### Established Patterns
- Constructor injection via `@RequiredArgsConstructor` (Lombok) or explicit constructors
- Service facade pattern: existing `ContestServiceImpl` already delegates to `RankingServiceImpl`
- Single interface per module, multiple impls possible

### Integration Points
- `ForumController` depends on `ForumService` — facade preserves this contract
- `ContestController` depends on `ContestService` — facade preserves this contract
- `SubmissionController` or similar depends on `CodeExecutionService` — facade preserves this contract

</code_context>

<specifics>
## Specific Ideas

No specific "I want it like X" moments — standard service decomposition following established patterns in the codebase.

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope.

</deferred>
