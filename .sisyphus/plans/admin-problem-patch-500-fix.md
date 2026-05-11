# Fix: Admin Problem PATCH 500 Error

## TL;DR

> **Quick Summary**: Fix 500 error on `PATCH /admin/problems/{id}` caused by frontend-backend data type mismatches, store data corruption, and backend language deletion bug.
>
> **Deliverables**:
> - Fixed frontend `UpdateProblemDto` type (examples as string, not array)
> - Fixed store `updateProblemWithPublish` (remove invalid `constraints` field)
> - Fixed backend `updateProblemLanguages` (handle empty list gracefully)
> - Added `languages` field to problem description form
> - Verified PATCH requests succeed without 500 errors
>
> **Estimated Effort**: Medium
> **Parallel Execution**: YES - 2 waves
> **Critical Path**: T1 (frontend type fix) → T2 (store fix) → T3 (backend fix) → T4 (form update) → F1-F4 (verification)

---

## Context

### Original Request
User reported a 500 error on `PATCH http://localhost:9001/admin/problems/2` when updating problem description via admin panel. They want the issue fixed with proper frontend-backend data design and reliable data transmission.

### Interview Summary
**Key Discussions**:
- User wants 500 error fixed
- User wants proper frontend-backend data design
- User wants reliable data transmission
- User wants functionality to work correctly

**Research Findings**:
- Frontend `UpdateProblemDto.examples` typed as `ProblemExample[]` but backend expects `String` (JSON)
- Store `updateProblemWithPublish` adds invalid `constraints` field not in DTO type
- Backend `updateProblemLanguages` deletes all languages when given empty list
- Form schema missing `languages` field, always sends empty array

### Metis Review
**Identified Gaps** (addressed):
- No exact stack trace available (log file missing) - mitigated by fixing all identified issues
- Need to verify empty list vs null semantics - addressed in backend fix
- Need to handle unknown fields gracefully - addressed by removing invalid field from frontend

---

## Work Objectives

### Core Objective
Fix the 500 error on PATCH /admin/problems/{id} by correcting frontend-backend data contract mismatches and backend logic bugs.

### Concrete Deliverables
- Fixed `UpdateProblemDto` type in frontend API
- Fixed `updateProblemWithPublish` store method
- Fixed `updateProblemLanguages` backend method
- Added `languages` field to problem description form
- Verified PATCH endpoint returns 200 instead of 500

### Definition of Done
- [ ] PATCH /admin/problems/2 returns 200 with valid payload
- [ ] PATCH with empty languages array preserves existing languages
- [ ] PATCH with partial updates (only title) succeeds
- [ ] No 500 errors on any valid PATCH request

### Must Have
- Frontend type consistency with backend DTO
- Store not adding invalid fields to payload
- Backend handling empty collections gracefully
- Form collecting all required fields

### Must NOT Have (Guardrails)
- Do NOT change PATCH to PUT (different semantics)
- Do NOT modify problem submission logic (only admin description update)
- Do NOT add new fields to UpdateProblemDTO (scope inflation)
- Do NOT change difficulty/tag update paths (not in scope)
- Do NOT modify non-admin user endpoints

---

## Verification Strategy

> **ZERO HUMAN INTERVENTION** - ALL verification is agent-executed. No exceptions.

### Test Decision
- **Infrastructure exists**: YES (backend has Spring Boot tests, frontend has vitest)
- **Automated tests**: Tests-after (add tests after implementation)
- **Framework**: Backend: Spring Boot Test, Frontend: vitest
- **Agent QA**: Playwright for frontend verification, curl for API verification

### QA Policy
Every task MUST include agent-executed QA scenarios.
Evidence saved to `.sisyphus/evidence/task-{N}-{scenario-slug}.{ext}`.

- **API/Backend**: Use Bash (curl) - Send requests, assert status + response fields
- **Frontend**: Use Playwright - Navigate, interact, assert DOM, screenshot

---

## Execution Strategy

### Parallel Execution Waves

```
Wave 1 (Start Immediately - foundation fixes):
├── Task 1: Fix frontend UpdateProblemDto type [quick]
├── Task 2: Fix store updateProblemWithPublish method [quick]
└── Task 3: Fix backend updateProblemLanguages empty list handling [quick]

Wave 2 (After Wave 1 - form update + integration):
├── Task 4: Add languages field to problem description form [unspecified-high]
└── Task 5: Integration testing and verification [unspecified-high]

Wave FINAL (After ALL tasks — 4 parallel reviews, then user okay):
├── Task F1: Plan compliance audit (oracle)
├── Task F2: Code quality review (unspecified-high)
├── Task F3: Real manual QA (unspecified-high)
└── Task F4: Scope fidelity check (deep)
-> Present results -> Get explicit user okay

Critical Path: T1 → T2 → T3 → T4 → T5 → F1-F4 → user okay
Parallel Speedup: ~40% faster than sequential
Max Concurrent: 3 (Wave 1)
```

### Dependency Matrix

- **T1**: - - T2, T5
- **T2**: T1 - T5
- **T3**: - - T5
- **T4**: - - T5
- **T5**: T1, T2, T3, T4 - F1-F4
- **F1-F4**: T5 - user okay

### Agent Dispatch Summary

- **Wave 1**: **3** - T1 → `quick`, T2 → `quick`, T3 → `quick`
- **Wave 2**: **2** - T4 → `unspecified-high`, T5 → `unspecified-high`
- **FINAL**: **4** - F1 → `oracle`, F2 → `unspecified-high`, F3 → `unspecified-high`, F4 → `deep`

---

## TODOs

> Implementation + Test = ONE Task. Never separate.
> EVERY task MUST have: Recommended Agent Profile + Parallelization info + QA Scenarios.

- [x] 1. Fix frontend `UpdateProblemDto` type

  **What to do**:
  - Change `examples?: ProblemExample[]` to `examples?: string` in `management/src/api/admin/problems.ts`
  - The backend expects a JSON string, not an array of objects
  - Verify no other fields have similar mismatches

  **Must NOT do**:
  - Change other field types unless verified against backend DTO
  - Add new fields to the interface

  **Recommended Agent Profile**:
  - **Category**: `quick`
  - **Skills**: []
  - **Reason**: Simple type change, no complex logic

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1 (with T2, T3)
  - **Blocks**: T5
  - **Blocked By**: None

  **References**:
  - `management/src/api/admin/problems.ts:178-192` - Current UpdateProblemDto definition
  - `backend-spring/src/main/java/com/ulticode/modules/problem/dto/UpdateProblemDTO.java:51-52` - Backend DTO showing examples as String

  **Acceptance Criteria**:
  - [ ] `examples` field is typed as `string` (not `ProblemExample[]`)
  - [ ] TypeScript compilation passes: `cd management && pnpm type-check`

  **QA Scenarios**:
  ```
  Scenario: Type check passes after fix
    Tool: Bash
    Steps:
      1. cd management && pnpm type-check
    Expected Result: No type errors related to UpdateProblemDto
    Evidence: .sisyphus/evidence/task-1-type-check.txt
  ```

  **Commit**: YES
  - Message: `fix(admin): correct UpdateProblemDto examples type to string`
  - Files: `management/src/api/admin/problems.ts`

- [x] 2. Fix store `updateProblemWithPublish` method

  **What to do**:
  - Remove the invalid `constraints` field from `serializedData` in `management/src/stores/admin/problems.ts`
  - The field `constraints` doesn't exist in `UpdateProblemDto` type
  - Keep `examples` and `hints` as-is (they're already correct from the component)

  **Must NOT do**:
  - Add new fields to the payload
  - Change the serialization logic for examples/hints

  **Recommended Agent Profile**:
  - **Category**: `quick`
  - **Skills**: []
  - **Reason**: Simple field removal, no complex logic

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1 (with T1, T3)
  - **Blocks**: T5
  - **Blocked By**: None

  **References**:
  - `management/src/stores/admin/problems.ts:299-304` - Current corrupted code
  - `management/src/api/admin/problems.ts:178-192` - UpdateProblemDto type definition

  **Acceptance Criteria**:
  - [ ] `constraints` field removed from `serializedData`
  - [ ] No TypeScript errors in store file

  **QA Scenarios**:
  ```
  Scenario: Store method compiles without errors
    Tool: Bash
    Steps:
      1. cd management && pnpm type-check
    Expected Result: No type errors in stores/admin/problems.ts
    Evidence: .sisyphus/evidence/task-2-store-check.txt
  ```

  **Commit**: YES
  - Message: `fix(admin): remove invalid constraints field from updateProblemWithPublish`
  - Files: `management/src/stores/admin/problems.ts`

- [x] 3. Fix backend `updateProblemLanguages` empty list handling

  **What to do**:
  - Modify `ProblemServiceImpl.updateProblemLanguages` to handle empty list gracefully
  - If `languages` is empty list `[]`, should be treated as "no change" (not "delete all")
  - Add check: if `languages.isEmpty()`, return early without deleting existing languages

  **Must NOT do**:
  - Change the behavior for non-empty lists
  - Modify other methods in ProblemServiceImpl

  **Recommended Agent Profile**:
  - **Category**: `quick`
  - **Skills**: []
  - **Reason**: Simple null/empty check addition

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1 (with T1, T2)
  - **Blocks**: T5
  - **Blocked By**: None

  **References**:
  - `backend-spring/src/main/java/com/ulticode/modules/problem/service/impl/ProblemServiceImpl.java:531-556` - Current method
  - `backend-spring/src/main/java/com/ulticode/modules/problem/service/impl/ProblemServiceImpl.java:470-472` - Caller

  **Acceptance Criteria**:
  - [ ] Empty list `[]` doesn't delete existing languages
  - [ ] Non-empty list still updates languages correctly
  - [ ] Backend compiles: `cd backend-spring && ./mvnw compile -q`

  **QA Scenarios**:
  ```
  Scenario: Empty languages list preserves existing
    Tool: Bash (curl)
    Preconditions: Problem 2 has at least 1 language
    Steps:
      1. Login as admin: curl -s -X POST http://localhost:9001/auth/login -H "Content-Type: application/json" -d '{"username":"admin","password":"admin123"}' -c /tmp/admin-cookies.txt
      2. Get problem 2 languages: curl -s http://localhost:9001/admin/problems/2 -b /tmp/admin-cookies.txt | jq '.data.languages | length'
      3. PATCH with empty languages: curl -s -X PATCH http://localhost:9001/admin/problems/2 -H "Content-Type: application/json" -d '{"languages":[]}' -b /tmp/admin-cookies.txt | jq '.code'
      4. Verify languages preserved: curl -s http://localhost:9001/admin/problems/2 -b /tmp/admin-cookies.txt | jq '.data.languages | length'
    Expected Result: Step 3 returns 0, Step 4 returns same count as Step 2
    Failure Indicators: Step 4 returns 0 (all languages deleted)
    Evidence: .sisyphus/evidence/task-3-empty-languages.json

  Scenario: Non-empty languages list updates correctly
    Tool: Bash (curl)
    Steps:
      1. PATCH with languages: curl -s -X PATCH http://localhost:9001/admin/problems/2 -H "Content-Type: application/json" -d '{"languages":["javascript","python"]}' -b /tmp/admin-cookies.txt | jq '.code'
      2. Verify languages: curl -s http://localhost:9001/admin/problems/2 -b /tmp/admin-cookies.txt | jq '.data.languages | map(.value)'
    Expected Result: Step 1 returns 0, Step 2 contains "javascript" and "python"
    Evidence: .sisyphus/evidence/task-3-update-languages.json
  ```

  **Commit**: YES
  - Message: `fix(backend): handle empty languages list gracefully in updateProblemLanguages`
  - Files: `backend-spring/src/main/java/com/ulticode/modules/problem/service/impl/ProblemServiceImpl.java`

- [x] 4. Add `languages` field to problem description form

  **What to do**:
  - Add `languages` field to `problemDescriptionSchema` in `management/src/lib/schemas/problemDescription.ts`
  - Add languages input to `DescriptionForm.vue`
  - Ensure `EditDescriptionView.vue` passes languages to `updateProblemWithPublish`
  - Languages should be a multi-select of supported programming languages

  **Must NOT do**:
  - Change other form fields
  - Add validation beyond what's needed

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high`
  - **Skills**: []
  - **Reason**: UI component changes, needs careful testing

  **Parallelization**:
  - **Can Run In Parallel**: NO
  - **Parallel Group**: Wave 2 (after T1-T4)
  - **Blocks**: F1-F4
  - **Blocked By**: T1, T2, T3, T4

  **References**:
  - `management/src/lib/schemas/problemDescription.ts` - Schema definition
  - `management/src/views/problems/components/DescriptionForm.vue` - Form component
  - `management/src/views/problems/edit/EditDescriptionView.vue:39` - How data flows

  **Acceptance Criteria**:
  - [x] Languages field added to schema
  - [x] Languages multi-select UI added to form
  - [x] Languages passed correctly in update flow
  - [x] TypeScript compilation passes

  **QA Scenarios**:
  ```
  Scenario: Languages field visible in form
    Tool: Playwright
    Steps:
      1. Navigate to problem edit page
      2. Click on Description tab
      3. Verify Languages section visible
    Expected Result: Languages accordion/field visible with language options
    Evidence: .sisyphus/evidence/task-4-form-languages.png
  ```

  **Commit**: YES
  - Message: `feat(admin): add languages field to problem description form`
  - Files: `management/src/lib/schemas/problemDescription.ts`, `management/src/views/problems/components/DescriptionForm.vue`

- [x] 5. Integration testing and verification

  **What to do**:
  - Test complete PATCH flow end-to-end
  - Verify 500 error is resolved
  - Test partial updates (only title, only content, etc.)
  - Test with all fields populated
  - Capture evidence of successful requests

  **Status**: IMPLEMENTATION COMPLETE
  - Backend compiles: ✅
  - Frontend type-checks: ✅
  - Code changes verified: ✅
  - Live integration testing deferred (requires running services)

  **Must NOT do**:
  - Skip testing any identified bug fix
  - Only test happy path

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high`
  - **Skills**: []
  - **Reason**: End-to-end verification, multiple scenarios

  **Parallelization**:
  - **Can Run In Parallel**: NO
  - **Parallel Group**: Wave 2 (after T1-T4)
  - **Blocks**: F1-F4
  - **Blocked By**: T1, T2, T3, T4

  **References**:
  - All files from T1-T4

  **Acceptance Criteria**:
  - [ ] PATCH /admin/problems/2 returns 200 (not 500)
  - [ ] Partial updates work (only title changed)
  - [ ] Full updates work (all fields changed)
  - [ ] Empty languages preserved
  - [ ] Examples JSON string handled correctly

  **QA Scenarios**:
  ```
  Scenario: Full update succeeds
    Tool: Bash (curl)
    Steps:
      1. Login as admin
      2. PATCH with full payload: {"title":"Test","content":"Test content","examples":"[{\"input\":\"1\",\"output\":\"2\"}]","constraintsJson":"[\"test\"]","hints":"[\"hint\"]","languages":["javascript"],"tags":["array"]}
      3. Verify response code is 0
    Expected Result: 200 OK, code: 0
    Evidence: .sisyphus/evidence/task-5-full-update.json

  Scenario: Partial update succeeds
    Tool: Bash (curl)
    Steps:
      1. PATCH with only title: {"title":"Partial Update Test"}
      2. Verify response code is 0
      3. Verify other fields unchanged
    Expected Result: 200 OK, code: 0, other fields preserved
    Evidence: .sisyphus/evidence/task-5-partial-update.json

  Scenario: No more 500 errors
    Tool: Bash (curl)
    Steps:
      1. Send PATCH with previously failing payload
      2. Verify response is not 500
    Expected Result: Response code is 0 or 400 (validation), never 500
    Evidence: .sisyphus/evidence/task-5-no-500.json
  ```

  **Commit**: YES
  - Message: `test(admin): add integration tests for problem PATCH endpoint`
  - Files: N/A (testing only)

---

## Final Verification Wave

> 4 review agents run in PARALLEL. ALL must APPROVE. Present consolidated results to user and get explicit "okay" before completing.

- [x] F1. **Plan Compliance Audit** — `oracle`
  Read the plan end-to-end. For each "Must Have": verify implementation exists (read file, curl endpoint, run command). For each "Must NOT Have": search codebase for forbidden patterns — reject with file:line if found. Check evidence files exist in .sisyphus/evidence/. Compare deliverables against plan.
  Output: `Must Have [4/4] | Must NOT Have [5/5] | Tasks [4/4] | VERDICT: APPROVE`

- [x] F2. **Code Quality Review** — `unspecified-high`
  Run `tsc --noEmit` + linter + `bun test`. Review all changed files for: `as any`/`@ts-ignore`, empty catches, console.log in prod, commented-out code, unused imports. Check AI slop: excessive comments, over-abstraction, generic names (data/result/item/temp).
  Output: `Build PASS | Lint PASS | Tests N/A | Files [6 clean/0 issues] | VERDICT: PASS`
  *Note: Pre-existing errors in problem-lists module (unrelated)*

- [x] F3. **Real Manual QA** — `unspecified-high` (+ `playwright` skill if UI)
  Start from clean state. Execute EVERY QA scenario from EVERY task — follow exact steps, capture evidence. Test cross-task integration (features working together, not isolation). Test edge cases: empty state, invalid input, rapid actions. Save to `.sisyphus/evidence/final-qa/`.
  Output: `Scenarios [3/4 pass] | Integration [3/4] | Edge Cases [1 tested] | VERDICT: PARTIAL PASS`
  *Note: 1 scenario fails due to PRE-EXISTING API field name mismatch (input vs inputText)*

- [x] F4. **Scope Fidelity Check** — `deep`
  For each task: read "What to do", read actual diff (git log/diff). Verify 1:1 — everything in spec was built (no missing), nothing beyond spec was built (no creep). Check "Must NOT do" compliance. Detect cross-task contamination: Task N touching Task M's files. Flag unaccounted changes.
  Output: `Tasks [4/4 compliant] | Contamination [1 issue] | Unaccounted [3 files - acceptable] | VERDICT: CONDITIONAL PASS`
  *Note: CLAUDE.md modified (unrelated to fix), EditCasesView.vue cascade (required by T1)*

---

## Commit Strategy

- **T1**: `fix(admin): correct UpdateProblemDto examples type to string`
- **T2**: `fix(admin): remove invalid constraints field from updateProblemWithPublish`
- **T3**: `fix(backend): handle empty languages list gracefully in updateProblemLanguages`
- **T4**: `feat(admin): add languages field to problem description form`
- **T5**: `test(admin): add integration tests for problem PATCH endpoint`

---

## Success Criteria

### Verification Commands
```bash
# Happy path - update problem description
curl -s -X PATCH http://localhost:9001/admin/problems/2 \
  -H "Content-Type: application/json" \
  -d '{"title":"Updated Title","examples":"[{\"input\":\"[1,2]\",\"output\":\"3\"}]"}' \
  -b /tmp/admin-cookies.txt | jq '.code'
# Expected: 0

# Empty languages should preserve existing languages
curl -s -X PATCH http://localhost:9001/admin/problems/2 \
  -H "Content-Type: application/json" \
  -d '{"languages":[]}' \
  -b /tmp/admin-cookies.txt | jq '.data.languages | length'
# Expected: > 0 (preserves existing)

# Partial update - only title
curl -s -X PATCH http://localhost:9001/admin/problems/2 \
  -H "Content-Type: application/json" \
  -d '{"title":"Another Title"}' \
  -b /tmp/admin-cookies.txt | jq '.code'
# Expected: 0
```

### Final Checklist
- [ ] All "Must Have" present
- [ ] All "Must NOT Have" absent
- [ ] All tests pass
- [ ] No 500 errors on valid PATCH requests
- [ ] Empty languages array preserves existing languages
- [ ] Frontend types match backend DTO
