# Implementation Plan: Problem Detail Repair Iteration

> Generated: 2026-05-28
> Based on: docs/problem-detail-repair-iteration-plan.md + docs/problem-detail-frontend-backend-alignment-analysis.md
> Multi-model analysis: Codex + Gemini

---

## Current State Assessment

Several P0 issues from the original analysis are **already fixed in the current checkout**:

- **SecurityConfig**: `/problems/**` removed from `PUBLIC_ENDPOINTS`; explicit GET allowlist for `/problems`, `/problems/*`, `/problems/slug/**`, `/problems/*/adjacent`; `GET /edge-operations/**` also public.
- **SecurityUtil.getCurrentUserId()**: Excludes `"anonymousUser"`.
- **Flyway migration**: `V111__create_test_cases.sql` exists.
- **ProblemServiceImpl**: Returns real `submissionCount`, `solutionCount`, `tags`, `detail.content`, and `interactions` (likes/dislikes/favorites).
- **CodeExecutionService**: Rejects empty test cases with `BAD_REQUEST`; has `VERDICT_PRIORITY` map for top-level verdict aggregation.
- **TestResultsView.vue**: Already maps all verdicts (Accepted, WA, RE, TLE, MLE, CE, System Error) and reads `errorMessage ?? error_message`.

**Remaining gaps** are categorized below.

---

## Task Type

- [x] Frontend (Gemini-informed)
- [x] Backend (Codex-informed)
- [x] Fullstack (Parallel tracks)

---

## Remaining Issues

### Backend

| # | Issue | File(s) |
|---|-------|---------|
| BE-1 | **Admin test-case endpoints incomplete**: Missing `POST /bulk`, `PUT /reorder`, `GET /export`. Management frontend already calls these. | `AdminTestCaseController.java`, `AdminTestCaseService.java` |
| BE-2 | **Sandbox volume path uses `$(pwd)`**: `ProcessBuilder` does not expand shell variables. Leads to seccomp profile mount failure. | `SandboxServiceImpl.java:172`, `:214` |
| BE-3 | **No TypeScript support**: `CodeExecutionHelper` only supports `javascript/python/java/c/cpp`. Frontend lists TypeScript but runtime fails. | `CodeExecutionHelper.java`, `CodeExecutionHelperImpl.java` |
| BE-4 | **SecurityUtil.getCurrentUsername() trusts anonymous contexts**: Uses `authentication.getDetails()` without excluding anonymous. | `SecurityUtil.java:36` |
| BE-5 | **Worker hidden-case behavior unverified**: Need to confirm `JudgeWorkerProcessor` reads `test_cases` (sample + hidden) and constructs `RunSubmissionDTO` with structured `inputs`. | `JudgeWorkerProcessor.java` |

### Frontend

| # | Issue | File(s) |
|---|-------|---------|
| FE-1 | **DescriptionView uses `summary` for content**: `problemDescription.content` binds to `props.problem.summary` instead of `props.problem.content`. | `DescriptionView.vue:74` |
| FE-2 | **TestCaseView `addCase` omits `output`**: New custom cases lack `expectedOutput`, causing empty expected fields in results. | `TestCaseView.vue:135-148` |
| FE-3 | **Run loading is fake**: `handleRun` sets a fixed 1.2s `setTimeout`; should bind to actual `runSubmission` promise lifecycle. | `LayoutHeaderCenter.vue:38-50` |
| FE-4 | **Submit lacks auth guard**: `handleSubmit` sends request before checking auth, resulting in backend 401/404 and poor UX. | `LayoutHeaderCenter.vue:52-80` |
| FE-5 | **Default language is TypeScript**: Editor defaults to `"typescript"`, which backend cannot execute. | `LayoutHeaderCenter.vue:57`, `useProblemDetail.ts:63` |
| FE-6 | **Ghost `userId` query param**: `fetchProblemDetailById` still appends `?userId=`; backend ignores it. | `problem-detail.ts:65-67` |
| FE-7 | **Problem notes endpoint missing**: `ProblemNotesDrawer` calls `/problems/{id}/note` (404). | `interaction.ts`, `ProblemNotesDrawer.vue` |

---

## Implementation Steps

### Track A: Backend (Codex)

#### Step A1 — Complete Admin Test-Case Endpoints
- **File**: `AdminTestCaseController.java`
- **Action**: Add `POST /bulk`, `PUT /reorder`, `GET /export`.
- **Action**: Add corresponding methods in `AdminTestCaseService` (`bulkImport`, `reorder`, `export`).
- **Deliverable**: Management frontend `testCasesApi` has 100% backend coverage.

#### Step A2 — Fix Sandbox Volume Path
- **File**: `SandboxServiceImpl.java`
- **Action**: Replace `"$(pwd)/docker/sandbox:/seccomp-profile:ro"` with an absolute path resolved at runtime (e.g., from `application.yml` or `System.getProperty("user.dir")`).
- **Deliverable**: Docker `--volume` argument uses an absolute path; seccomp profile mounts successfully.

#### Step A3 — Add TypeScript Support (or Filter It)
- **Option 1 (Short-term)**: Filter `typescript` from `buildLanguages()` in `ProblemServiceImpl` so detail response only lists executable languages.
- **Option 2 (Medium-term)**: Add `typescript` to `CodeExecutionHelperImpl` via `ts-node` or `tsc + node` wrapper in the sandbox Dockerfile.
- **Recommendation**: Implement Option 1 immediately; Option 2 as a follow-up iteration.
- **Deliverable**: Language dropdown never offers an unexecutable language.

#### Step A4 — Harden SecurityUtil
- **File**: `SecurityUtil.java`
- **Action**: In `getCurrentUsername()`, add `&& !"anonymousUser".equals(authentication.getName())` guard.
- **Deliverable**: Anonymous contexts return `null` for both user ID and username.

#### Step A5 — Verify Worker Test-Case Flow
- **File**: `JudgeWorkerProcessor.java`
- **Action**: Read and trace the logic; confirm it:
  1. Queries `test_cases` by `problemId`.
  2. Includes both `is_sample=true` and `is_hidden=true` cases.
  3. Maps `inputs` JSON into `RunSubmissionDTO.RunTestCase`.
  4. Returns `System Error` with a clear note when no official cases exist.
- **Deliverable**: Worker test passes for Two Sum with hidden cases.

### Track B: Frontend (Gemini)

#### Step B1 — Fix Content Mapping
- **File**: `DescriptionView.vue`
- **Action**: Change `content: props.problem.summary || ""` to `content: props.problem.content || props.problem.summary || ""`.
- **Deliverable**: Full Markdown content renders when `detail.content` is present.

#### Step B2 — Fix Custom Case Output Field
- **File**: `TestCaseView.vue`
- **Action**: In `addCase()`, initialize `output: ""` (or `template?.output ?? ""`).
- **Deliverable**: Custom cases have a visible Expected Output field; no undefined errors in `TestResultsView`.

#### Step B3 — Bind Run/Submit Loading to Real Promise
- **File**: `LayoutHeaderCenter.vue`, `useProblemDetail.ts`
- **Action**:
  - Remove fixed `setTimeout` in `handleRun`.
  - Move `isRunning` / `isSubmitting` into a centralized execution store (e.g., `useProblemExecutionStore`) or manage them via `finally` blocks in `useProblemDetail.ts`.
  - `handleRun` should await the `runSubmission` promise and toggle loading around it.
- **Deliverable**: Run/submit buttons show real pending state.

#### Step B4 — Add Auth Guard to Submit
- **File**: `LayoutHeaderCenter.vue`
- **Action**: Before `createSubmission` / `submitContestProblem`, check `useAuthStore().isAuthenticated`. If anonymous, trigger login modal or toast and abort.
- **Deliverable**: Anonymous users see a login prompt instead of a backend error.

#### Step B5 — Change Default Language to JavaScript
- **File**: `LayoutHeaderCenter.vue`, `useProblemDetail.ts`
- **Action**: Replace default `"typescript"` with `"javascript"` everywhere until backend supports TypeScript.
- **Deliverable**: Default language is executable out of the box.

#### Step B6 — Remove Ghost userId Parameter
- **File**: `console/src/api/problem-detail.ts`
- **Action**: Remove `userId` parameter from `fetchProblemDetailById` and stop appending `?userId=`.
- **Deliverable**: Cleaner request URLs; no ignored query parameters.

#### Step B7 — Handle Missing Notes Endpoint
- **File**: `ProblemNotesDrawer.vue`, `interaction.ts`
- **Action**: Either (a) implement backend `GET/POST /problems/{id}/note` endpoints, or (b) hide the notes button when the endpoint is unavailable.
- **Recommendation**: Short-term hide; implement notes backend in Iteration 4.
- **Deliverable**: Users never open a drawer that immediately 404s.

---

## Key Files and Operations

| File | Operation | Description |
|------|-----------|-------------|
| `backend-spring/.../admin/controller/AdminTestCaseController.java` | Modify | Add bulk, reorder, export endpoints |
| `backend-spring/.../admin/service/AdminTestCaseService.java` | Modify | Add bulkImport, reorder, export methods |
| `backend-spring/.../submission/service/impl/SandboxServiceImpl.java` | Modify | Fix volume path (absolute) |
| `backend-spring/.../submission/service/impl/CodeExecutionHelperImpl.java` | Modify | Optionally add TypeScript wrapper |
| `backend-spring/.../common/util/SecurityUtil.java` | Modify | Harden getCurrentUsername() |
| `backend-spring/.../problem/service/impl/ProblemServiceImpl.java` | Modify | Filter unexecutable languages from buildLanguages |
| `console/src/views/problems/description/DescriptionView.vue` | Modify | Use content before summary |
| `console/src/views/problems/test/TestCaseView.vue` | Modify | Initialize output in addCase |
| `console/src/views/problems/headers/LayoutHeaderCenter.vue` | Modify | Real promise loading + auth guard + default language |
| `console/src/views/problems/useProblemDetail.ts` | Modify | Remove userId param, fix default language |
| `console/src/api/problem-detail.ts` | Modify | Drop userId argument |
| `console/src/components/problem/ProblemNotesDrawer.vue` | Modify | Hide if endpoint unavailable |

---

## Risks and Mitigation

| Risk | Mitigation |
|------|------------|
| Sandbox path fix breaks dev vs prod differently | Resolve path from `application.yml` with a sensible default; test on both environments |
| Filtering TypeScript surprises users who had it selected | Persist language per-user in localStorage; only filter the default/init list |
| Auth guard too aggressive | Allow anonymous run (if product decision) but block submit; test with both auth states |
| Admin bulk/reorder DTO mismatch with management frontend | Compare management `test-cases.ts` DTOs before implementing |

---

## Testing Checklist

- [ ] Anonymous `GET /problems/slug/two-sum` → 200 with `detail.content` populated
- [ ] Anonymous `POST /problems/1/submissions` → 401
- [ ] `POST /problems/1/submissions/run` with empty cases → 400
- [ ] JavaScript Two Sum correct code run → Accepted
- [ ] JavaScript Two Sum wrong code run → Wrong Answer
- [ ] Admin bulk import test cases → 200
- [ ] Frontend run button shows real spinner until response
- [ ] Frontend submit without login → login prompt, no network request
- [ ] Description tab shows full Markdown content, not just summary

---

## SESSION_ID (for /ccg:execute use)

- **CODEX_SESSION**: `019e6e6c-72f1-75b2-a464-5abebcd9cc21`
- **GEMINI_SESSION**: `865bffc8-897e-48a7-ac44-a40c811e7401`
