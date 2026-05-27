# Local Code Review: Problem Detail Frontend-Backend Alignment Fix

**Reviewed**: 2026-05-27
**Branch**: feat/problem-detail-alignment
**Decision**: APPROVE with comments

## Summary
Solid, focused changes that fix real alignment issues. Security boundary is correctly tightened, judge pipeline gets structured input support, and frontend mapping is fixed. A few quality nits and one edge-case concern worth addressing.

---

## Findings

### CRITICAL
None

### HIGH
None

### MEDIUM

#### 1. `ProblemServiceImpl.java:314-362` — `buildDetailResponse` method too long after additions
- **File**: `backend-spring/src/main/java/com/ulticode/modules/problem/service/impl/ProblemServiceImpl.java`
- **Lines**: ~314-362
- **Issue**: The `buildDetailResponse` method now exceeds 50 lines significantly. The interactions parsing logic adds ~25 lines of nested conditionals and try-catch inside an already large method.
- **Suggested fix**: Extract the interactions building logic into a private helper method:
  ```java
  private ProblemDetailResponse.InteractionData buildInteractions(ProblemDetail detail, String userId) { ... }
  ```

#### 2. `JudgeWorkerProcessor.java:293-305` — Potential ClassCastException on JSON inputs parsing
- **File**: `backend-spring/src/main/java/com/ulticode/modules/queue/processor/JudgeWorkerProcessor.java`
- **Lines**: ~293-305
- **Issue**: `objectMapper.readValue(tc.getInputs(), new TypeReference<List<Map<String, String>>>() {})` will throw if the JSON contains non-string values (e.g., `{"name": 123}`). The `TypeReference` doesn't protect against type coercion failures for nested values.
- **Suggested fix**: Use `List<Map<String, Object>>` and explicitly `.toString()` each value, or add a try-catch around the entire per-item mapping loop.

#### 3. `AdminTestCaseService.java:76-86` — No JSON validation on `inputs` field
- **File**: `backend-spring/src/main/java/com/ulticode/modules/admin/service/AdminTestCaseService.java`
- **Lines**: ~76-86
- **Issue**: The `inputs` field receives raw string from DTO and stores it directly. If invalid JSON is saved, `JudgeWorkerProcessor` will silently fall back to `inputText` with a warning. This could mask data quality issues.
- **Suggested fix**: Add validation in `CreateTestCaseDTO` or service layer to verify `inputs` is valid JSON before storing. Alternatively, accept this as documented fallback behavior.

#### 4. `ProblemServiceImpl.java:330` — TODO comment for favorites
- **File**: `backend-spring/src/main/java/com/ulticode/modules/problem/service/impl/ProblemServiceImpl.java`
- **Line**: 330
- **Issue**: `interactions.setFavorites(0); // TODO: query bookmark/edge_operations if needed` leaves a TODO in production code.
- **Suggested fix**: Either implement favorites query (via `EdgeOperationsService` or bookmark mapper) or remove the TODO and add a code comment explaining the intentional placeholder.

### LOW

#### 5. `CodeExecutionService.java:60-62` — Unused `passedCases` variable
- **File**: `backend-spring/src/main/java/com/ulticode/modules/submission/service/CodeExecutionService.java`
- **Lines**: ~60-62
- **Issue**: `passedCases` is computed but still used only for `RunResultDTO.builder().passedCases(passedCases)`. Not a bug, but the variable is now redundant since verdict is determined by priority, not pass count.
- **Suggested fix**: Consider if `passedCases` should still be included in the DTO, or if it should reflect the actual accepted count (which it does). No change required.

#### 6. `problem-detail.ts:142-155` — Inline type import in `as` cast
- **File**: `console/src/api/problem-detail.ts`
- **Lines**: ~142-155
- **Issue**: `response.interactions.viewer_reaction as import("@/types/problem-detail").ProblemReactionType` is valid TypeScript but slightly unusual. Consider importing `ProblemReactionType` at the top of the file for consistency.
- **Suggested fix**: Add `import type { ProblemReactionType } from "@/types/problem-detail";` at the top and use the imported type directly.

#### 7. `AdminTestCaseController.java` — Missing `@Validated` on class
- **File**: `backend-spring/src/main/java/com/ulticode/modules/admin/controller/AdminTestCaseController.java`
- **Issue**: The controller uses `@Valid` on method parameters but the class itself doesn't have `@Validated`. In Spring Boot, `@Valid` on method parameters works with `@Validated` at class level for method-level validation.
- **Note**: This is actually fine for `@RequestBody` — Spring MVC validates `@Valid` on `@RequestBody` without `@Validated` on the class. Marking as LOW since it works but may be inconsistent with other admin controllers.

---

## Validation Results

| Check | Result |
|---|---|
| Backend compile | Pass |
| Backend tests | Fail (pre-existing test dependency issues, unrelated) |
| Frontend type-check | Fail (pre-existing errors in comment-tree-builder.ts, MarkdownEdit.vue, DonutChart.vue, CodeEditor.vue, unrelated) |
| Build | Pass |

---

## Files Reviewed

| File | Action | Risk Level |
|---|---|---|
| `SecurityConfig.java` | Modified | Low |
| `SecurityUtil.java` | Modified | Low |
| `ProblemDetailResponse.java` | Modified | Low |
| `TestCase.java` | Modified | Low |
| `ProblemServiceImpl.java` | Modified | Medium (method length) |
| `JudgeWorkerProcessor.java` | Modified | Medium (JSON parsing edge case) |
| `CodeExecutionService.java` | Modified | Low |
| `CodeExecutionHelperImpl.java` | Modified | Low |
| `AdminTestCaseController.java` | Created | Low |
| `AdminTestCaseService.java` | Created | Low |
| `CreateTestCaseDTO.java` | Created | Low |
| `UpdateTestCaseDTO.java` | Created | Low |
| `console/src/api/problem-detail.ts` | Modified | Low |
| `console/src/types/test-results.ts` | Modified | Low |
| `console/src/views/problems/headers/LayoutHeaderCenter.vue` | Modified | Low |
| `console/src/views/problems/test/TestResultsView.vue` | Modified | Low |
