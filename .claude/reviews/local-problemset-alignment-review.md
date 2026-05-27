# Local Code Review: Problemset Frontend-Backend Alignment

**Reviewed**: 2026-05-27
**Scope**: 16 changed files (7 backend, 9 frontend)
**Decision**: APPROVE with comments

## Summary
Solid refactor that correctly shifts `/problemset` from client-side filtering on 20 items to server-side pagination/filtering on 50 items. Backend contracts are properly extended, SecurityConfig correctly opens public read endpoints, and problem-list detail now returns complete problem metadata. No security vulnerabilities found. Compilation and static analysis pass cleanly.

---

## Findings

### CRITICAL
None

### HIGH
None

### MEDIUM

#### 1. ProblemListDrawer only fetches 50 problems
**File**: `console/src/components/problem/ProblemListDrawer.vue:37`
**Issue**: `fetchProblems({})` loads page 1 with default pageSize (50). If the system has more than 50 problems, the drawer sidebar only shows a subset and local search cannot find problems beyond that subset.
**Suggested fix**: Either pass a high `pageSize` (e.g. 500) for the drawer, or implement scroll-based pagination inside the drawer.

#### 2. Duplicate loadProblems() trigger on initialCategory prop change
**File**: `console/src/components/problem/composables/useProblemExplorer.ts:46-52,130-135`
**Issue**: When `props.initialCategory` changes, the first watcher sets `selectedCategory` and calls `loadProblems()`. The second watcher then detects `selectedCategory` change and calls `loadProblems()` again. The `isLoading` guard prevents a double network request, but this is an inefficient pattern.
**Suggested fix**: Remove `void loadProblems()` from the `initialCategory` watcher and let the `selectedCategory` watcher handle it alone.

### LOW

#### 3. Dead code: numProblemsToShow
**File**: `console/src/components/problem/composables/useProblemExplorer.ts:35,263,307`
**Issue**: `numProblemsToShow` is declared, returned from the composable, set in `clearFilters()`, but never read or used to limit displayed results after the server-driven refactor.
**Suggested fix**: Remove `numProblemsToShow` entirely from state, return object, and `clearFilters()`.

#### 4. Redundant computed aliases
**File**: `console/src/components/problem/composables/useProblemExplorer.ts:137,139`
**Issue**: `filteredProblems` and `totalFilteredProblems` are thin wrappers around `enrichedProblems` and `total` with no added logic.
**Suggested fix**: Inline `enrichedProblems` and `total` directly where `filteredProblems` and `totalFilteredProblems` are consumed, or remove the aliases.

#### 5. TagVO id and label both set to tagName
**File**: `backend-spring/src/main/java/com/ulticode/modules/problem/service/impl/ProblemServiceImpl.java:798-800` and `ProblemListServiceImpl.java:170-172`
**Issue**: `tagVO.setId(dto.tagName())` and `tagVO.setLabel(dto.tagName())` set both fields to the same string. If the frontend ever needs the actual tag ID (UUID/slug), this mapping loses it.
**Suggested fix**: Verify whether `ProblemTagDTO` exposes a real tag ID; if so, use it for `setId()`.

#### 6. Stats hardcoded to zero
**File**: `backend-spring/src/main/java/com/ulticode/modules/problemlist/service/impl/ProblemListServiceImpl.java:205-210`
**Issue**: `statsVO.setSolvedCount(0)`, `setAttemptedCount(0)`, `setProgress(0.0)` are all hardcoded. This appears to be pre-existing dead code.
**Suggested fix**: Implement actual stats calculation or remove the fields until they are needed.

---

## Validation Results

| Check | Result | Notes |
|---|---|---|
| Type check | Pass | 0 new type errors (14 pre-existing in other files) |
| Lint | Pass | 0 new lint errors in changed files (6 pre-existing in other files) |
| Build | Pass | `./mvnw compile -q` succeeds cleanly |
| Tests | Skipped | Maven surefire plugin not cached and network unavailable |

---

## Files Reviewed

| File | Action | Verdict |
|---|---|---|
| `SecurityConfig.java` | Modified | Acceptable - public endpoints correctly added |
| `ProblemController.java` | Modified | Acceptable - `@ModelAttribute` is cleaner than individual params |
| `ProblemQueryDTO.java` | Modified | Acceptable - new fields properly annotated |
| `ProblemServiceImpl.java` | Modified | Acceptable - SQL injection safe (`{0}` placeholder binding) |
| `ProblemListDetailVO.java` | Modified | Acceptable - VO properly extended |
| `ProblemListServiceImpl.java` | Modified | Acceptable - batch tag fetching avoids N+1 |
| `problem.ts (API)` | Modified | Acceptable - robust tag normalization |
| `useProblemExplorer.ts` | Modified | Acceptable - server-driven logic is sound |
| `ProblemExplorer.vue` | Modified | Acceptable - clean delegation to composable |
| `ProblemListDrawer.vue` | Modified | Acceptable - minor data-limit concern noted |
| `ProblemResultList.vue` | Modified | Acceptable - casing updated correctly |
| `problem.ts (types)` | Modified | Acceptable - uppercase difficulty union |
| `ProblemListAnalytics.vue` | Modified | Acceptable - bucket casing updated |
| `useProblemListOperations.ts` | Modified | Acceptable - refresh-after-add prevents stale state |
| `recommendation.spec.ts` | Modified | Acceptable - test data updated to match new type |

---

## Security Checklist

- [x] No hardcoded secrets
- [x] No SQL injection (parameterized `apply()` with `{0}`)
- [x] No XSS vectors
- [x] Input validation present (blank/null checks before query building)
- [x] Auth/authorization verified (SecurityConfig additions are GET-only public reads)
