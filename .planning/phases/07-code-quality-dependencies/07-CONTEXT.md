# Phase 7: Code Quality & Dependencies - Context

**Gathered:** 2026-04-16
**Status:** Ready for planning

<domain>
## Phase Boundary

Improve backend code quality (precise exception handling, service class splitting) and frontend cleanliness (debug logging removal). Ensure dependency hygiene (no SNAPSHOT deps, no tracked secrets).

Scope is LIMITED to:
- Replacing broad `catch(Exception e)` / `catch(Throwable e)` with specific exception types in production backend code
- Splitting AdminAnalyticsServiceImpl into focused service classes (each under 300 lines) — the roadmap specifically targets this one
- Removing `console.log` and `console.warn` from production frontend code (console.error for error logging is acceptable)
- Removing SNAPSHOT dependencies from pom.xml and untracking management/.env from git

NOT in scope: Refactoring other oversized services (18 total >300 lines), adding new features, changing business logic.

</domain>

<decisions>
## Implementation Decisions

### Exception Handling Precision
- **D-01:** Replace all `catch(Exception e)` with specific exception types (e.g., `catch(IOException e)`, `catch(SQLException e)`, `catch(BusinessException e)`) — analyze the try block to determine which exceptions can actually be thrown
- **D-02:** Where multiple distinct exceptions are possible, use multi-catch `catch (IOException | SQLException e)` rather than broad Exception
- **D-03:** For cases where the try block genuinely can throw many exception types and they all need the same handling (logging + rethrow as BusinessException), keep `catch (Exception e)` BUT add a comment explaining why broad catch is intentional: `// broad catch: all failures map to same error response`
- **D-04:** Never catch `Throwable` — let JVM errors (OutOfMemoryError, etc.) propagate

### AdminAnalyticsServiceImpl Splitting
- **D-05:** Split by domain responsibility into focused services:
  - `AdminUserAnalyticsService` — weekly active users, peak hours, top users, retention
  - `AdminContentAnalyticsService` — problem completion by difficulty, trending problems, tag stats
  - `AdminPerformanceReportService` — JVM metrics, performance report generation
- **D-06:** Keep `AdminAnalyticsServiceImpl` as a facade that delegates to the new services — maintains backward compatibility for AdminAnalyticsController
- **D-07:** Each new service should be under 300 lines with clear single responsibility

### Frontend Debug Logging Cleanup
- **D-08:** Remove all `console.log` and `console.warn` statements from production code in console/ and management/
- **D-09:** Keep `console.error` for genuine error logging — this is acceptable per success criteria
- **D-10:** If a console.log is inside a debug utility or development-only code path guarded by `import.meta.env.DEV`, it can stay — but evaluate case by case

### Dependency Hygiene
- **D-11:** Replace SNAPSHOT versions in pom.xml with stable release versions
- **D-12:** Add `management/.env` to `.gitignore` and remove from git tracking (`git rm --cached management/.env`)
- **D-13:** Verify no other secrets (API keys, passwords) are tracked in git

### Claude's Discretion
- Exact exception types for each catch block — researcher can analyze try blocks
- Order of service splitting implementation — planner decides
- Which console.log instances are in DEV guards vs production code

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Project Architecture
- `CLAUDE.md` — Project overview, module structure, common commands
- `.planning/PROJECT.md` — Project vision and requirements
- `.planning/ROADMAP.md` — Phase 7 success criteria and requirements

### Prior Phase Context
- `.planning/phases/06-admin-functionality-performance/06-CONTEXT.md` — Prior phase decisions (admin analytics patterns)
- `.planning/phases/06-admin-functionality-performance/06-04-SUMMARY.md` — AdminAnalyticsServiceImpl was recently refactored for SQL aggregation

### Codebase Reference
- `backend-spring/src/main/java/com/ulticode/common/exception/` — Existing exception hierarchy (BusinessException, etc.)
- `backend-spring/pom.xml` — Dependencies to check for SNAPSHOT versions
- `.gitignore` — Verify management/.env exclusion

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `BusinessException` and `GlobalExceptionHandler` — existing exception hierarchy, broad catches should wrap into these
- `@CurrentUser`, `@RequireRole` annotations — security patterns already established

### Established Patterns
- Service layer pattern: `controller → service → mapper` with Result<T> response wrapper
- MyBatis-Plus for data access — mappers handle SQL, services handle business logic
- Frontend request utility auto-unwraps API responses

### Integration Points
- `AdminAnalyticsController` depends on `AdminAnalyticsServiceImpl` — splitting must maintain this API contract
- `backend-spring/pom.xml` — internal modules use SNAPSHOT versions (recommend-module, recommend-core)

### Current State
- 84 broad `catch(Exception|Throwable)` patterns across backend
- 18 ServiceImpl files exceed 300 lines (AdminAnalyticsServiceImpl at 495 lines is the roadmap target)
- 30 `console.log/warn` in console/ and management/ frontend code
- 2 SNAPSHOT dependencies in pom.xml
- `management/.env` is git-tracked (should not be)

</code_context>

<specifics>
## Specific Ideas

No specific requirements — standard code quality improvement practices apply.

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope.

</deferred>

---

*Phase: 07-code-quality-dependencies*
*Context gathered: 2026-04-16*
