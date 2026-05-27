# Plan: Problem Detail Frontend-Backend Alignment Fix

## Summary
修复题目详情页前后端在数据契约、安全边界、评测链路、交互接口上的全面对齐问题。当前页面"看起来像完整刷题页"，但正式评测依赖未落库的 `test_cases` 体系，安全配置放开了整个 `/problems/**`，用户交互与笔记接口未闭环，前端题面内容映射错误。本计划按 P0→P1→P2→P3 优先级分阶段修复。

## User Story
As a user solving problems on UltiCode,
I want the problem detail page to display correct content, enforce proper auth boundaries, run official test cases on submit, and show accurate interaction counts,
So that I can trust the platform's correctness, security, and completeness.

## Problem → Solution
**Current state**: Security misconfig allows anonymous writes; `test_cases` table missing breaks official judging; frontend renders `summary` instead of `content`; TypeScript language unsupported but listed; interaction counts show 0; notes API 404.
**Desired state**: Explicit public GET paths only; official judge pipeline works with seeded test cases; frontend renders full markdown content; only supported languages shown; public counts readable anonymously; notes either implemented or hidden.

## Metadata
- **Complexity**: XL
- **Source PRD**: `docs/problem-detail-frontend-backend-alignment-analysis.md`
- **PRD Phase**: P0 → P1 → P2 → P3 (all phases)
- **Estimated Files**: 25+

---

## UX Design

### Before
- Anonymous users can POST submissions and runs (silently fails or returns wrong errors)
- Problem description shows only short summary, missing full markdown content
- Test case panel shows generic "示例 N" labels, custom cases lack expected output
- Run results show Compile Error details blank (reads `error_message` but backend sends `errorMessage`)
- Language dropdown includes TypeScript → runtime "Unsupported language"
- Like/favorite counts always show 0 on public page
- Notes drawer opens but API returns 404

### After
- Unauthenticated users get 401 on submit/run; public read endpoints work without auth
- Full markdown content renders with examples, constraints, follow-up
- Test case labels use backend example data; custom cases clearly marked
- Compile Error details display correctly
- Language dropdown only shows executable languages
- Public interaction counts display correctly
- Notes feature either works end-to-end or is hidden until ready

### Interaction Changes
| Touchpoint | Before | After | Notes |
|---|---|---|---|
| `/problems/**` security | All permitted | Only explicit GET paths permitted | Must not break existing public read |
| Description markdown | Shows `summary` only | Shows `content` with `summary` fallback | Fix mapping in `problem-detail.ts:127-128` |
| Run empty cases | Returns `Accepted` | Returns `System Error` / empty rejection | Fix `CodeExecutionService` + `emptyResult` |
| Compile error display | Blank | Shows actual compiler output | Fix `errorMessage` ↔ `error_message` mapping |
| TypeScript language | Listed but unsupported | Removed from options | Filter in `buildLanguages` or frontend |
| Interaction counts | Always 0 | Real counts from `problem_details` | Backend compute + public endpoint |
| Official judge | No `test_cases` table | Migrated, seeded, admin CRUD ready | Flyway + admin controller |

---

## Mandatory Reading

| Priority | File | Lines | Why |
|---|---|---|---|
| P0 | `backend-spring/src/main/java/com/ulticode/common/config/SecurityConfig.java` | 40-79 | PUBLIC_ENDPOINTS definition |
| P0 | `backend-spring/src/main/java/com/ulticode/common/util/SecurityUtil.java` | 20-26 | `getCurrentUserId()` anonymousUser bug |
| P0 | `backend-spring/src/main/java/com/ulticode/modules/submission/controller/ProblemSubmissionController.java` | 100-143 | Current auth checks |
| P1 | `db-manager/migrations/V2__problem_schema.sql` | 43-54 | `problem_examples` table (no `test_cases`) |
| P1 | `backend-spring/src/main/java/com/ulticode/modules/problem/entity/TestCase.java` | 1-67 | Entity fields for migration design |
| P1 | `backend-spring/src/main/java/com/ulticode/modules/queue/processor/JudgeWorkerProcessor.java` | 120-290 | Worker reads `test_cases`, builds DTO |
| P2 | `console/src/api/problem-detail.ts` | 53-137 | Frontend mapping logic |
| P2 | `backend-spring/src/main/java/com/ulticode/modules/problem/service/impl/ProblemServiceImpl.java` | 286-336 | `buildDetailResponse` hardcoded values |
| P3 | `console/src/views/problems/test/TestResultsView.vue` | 20-176 | Verdict labels + `error_message` field |
| P3 | `backend-spring/src/main/java/com/ulticode/modules/submission/service/CodeExecutionService.java` | 27-47 | Verdict logic (only Accepted/Wrong Answer) |

---

## External Documentation

No external research needed — feature uses established internal patterns.

---

## Patterns to Mirror

### NAMING_CONVENTION
```java
// SOURCE: backend-spring/src/main/java/com/ulticode/modules/problem/dto/ProblemDetailResponse.java:15
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProblemDetailResponse { ... }
```
Backend DTOs use camelCase fields with `@JsonProperty("snake_case")` for API serialization. Follow existing `@JsonProperty` pattern for new fields.

### ERROR_HANDLING
```java
// SOURCE: backend-spring/src/main/java/com/ulticode/modules/submission/controller/ProblemSubmissionController.java:108-111
String userId = SecurityUtil.getCurrentUserId();
if (userId == null) {
    throw new BusinessException(ErrorCode.UNAUTHORIZED);
}
```
Controllers check `userId == null` and throw `BusinessException(ErrorCode.UNAUTHORIZED)`.

### LOGGING_PATTERN
```java
// SOURCE: backend-spring/src/main/java/com/ulticode/modules/queue/processor/JudgeWorkerProcessor.java
log.error("Judge failed for submission {}", submissionId, e);
```
Use SLF4J `log.error/info/debug` with parameterized messages. Never log user code content at INFO.

### REPOSITORY_PATTERN
```java
// SOURCE: backend-spring/src/main/java/com/ulticode/modules/problem/mapper/TestCaseMapper.java:15-37
@Mapper
public interface TestCaseMapper extends BaseMapper<TestCase> {
    default List<TestCase> findByProblemIdOrderByOrder(Long problemId) { ... }
    default List<TestCase> findSampleByProblemId(Long problemId) { ... }
}
```
MyBatis-Plus mapper extends `BaseMapper<Entity>`. Add default methods for custom queries using `LambdaQueryWrapper`.

### SERVICE_PATTERN
```java
// SOURCE: backend-spring/src/main/java/com/ulticode/modules/submission/service/impl/SubmissionServiceImpl.java:81-124
@Transactional
public SubmissionVO submit(String userId, CreateSubmissionDTO dto) { ... }
```
Service methods use constructor injection via `@RequiredArgsConstructor` or explicit constructor. Business logic validates, then delegates to mapper/queue.

### TEST_STRUCTURE
```java
// SOURCE: backend-spring/src/test/java/com/ulticode/modules/queue/processor/JudgeWorkerProcessorTest.java:72-119
@ExtendWith(MockitoExtension.class)
class JudgeWorkerProcessorTest {
    @Mock private TestCaseMapper testCaseMapper;
    @BeforeEach void setUp() { ... }
    @Test @DisplayName("...") void methodName_scenario_expected() { ... }
}
```
Tests use JUnit 5 + Mockito + AssertJ. Mock mappers and external services. Use `@Nested` for grouping.

### FRONTEND_API_PATTERN
```typescript
// SOURCE: console/src/api/problem-detail.ts:53-62
export async function fetchProblemDetailById(id: number | string, userId?: string): Promise<ProblemDetail> {
  const query = userId ? `?userId=${userId}` : "";
  const endpoint = isNumeric ? `/problems/${id}` : `/problems/slug/${id}`;
  const response = await apiGet<BackendProblemResponse>(`${endpoint}${query}`);
  return mapProblemDetail(response);
}
```
Frontend API functions use `apiGet<T>` / `apiPost<T>` from `@/utils/request`. Map backend response to frontend types in a dedicated mapper function.

---

## Files to Change

| File | Action | Justification |
|---|---|---|
| `backend-spring/src/main/java/com/ulticode/common/util/SecurityUtil.java` | UPDATE | Exclude `anonymousUser` from `getCurrentUserId()` |
| `backend-spring/src/main/java/com/ulticode/common/config/SecurityConfig.java` | UPDATE | Replace `/problems/**` with explicit public GET paths |
| `backend-spring/src/main/java/com/ulticode/modules/submission/controller/ProblemSubmissionController.java` | UPDATE | Use `isAuthenticated()` or null-check properly |
| `console/src/api/problem-detail.ts` | UPDATE | Map `detail.content` with fallback chain |
| `backend-spring/src/main/java/com/ulticode/modules/problem/service/impl/ProblemServiceImpl.java` | UPDATE | Compute real `submissionCount`, `solutionCount`, `tags` |
| `backend-spring/src/main/java/com/ulticode/modules/problem/dto/ProblemDetailResponse.java` | UPDATE | Add `interactions` field |
| `console/src/types/test-results.ts` | UPDATE | Add `errorMessage` alias field |
| `console/src/views/problems/test/TestResultsView.vue` | UPDATE | Read `errorMessage ?? error_message`; fix verdict labels |
| `backend-spring/src/main/java/com/ulticode/modules/submission/service/CodeExecutionService.java` | UPDATE | Return error for empty test cases; improve verdict priority |
| `backend-spring/src/main/java/com/ulticode/modules/submission/service/impl/CodeExecutionHelperImpl.java` | UPDATE | `emptyResult` returns `System Error` not `Accepted` |
| `db-manager/migrations/V{next}__create_test_cases.sql` | CREATE | Create `test_cases` table matching `TestCase` entity |
| `db-manager/migrations/V{next+1}__seed_test_cases.sql` | CREATE | Seed sample+hidden cases for existing problems |
| `backend-spring/src/main/java/com/ulticode/modules/admin/controller/AdminTestCaseController.java` | CREATE | CRUD endpoints for admin test case management |
| `backend-spring/src/main/java/com/ulticode/modules/admin/service/AdminTestCaseService.java` | CREATE | Service layer for test case CRUD |
| `backend-spring/src/main/java/com/ulticode/modules/admin/dto/AdminTestCaseDTO.java` | CREATE | DTOs for create/update/list |
| `backend-spring/src/main/java/com/ulticode/modules/problem/mapper/TestCaseMapper.java` | UPDATE | Add `insertBatch`, `updateOrder` if needed |
| `backend-spring/src/main/java/com/ulticode/modules/queue/processor/JudgeWorkerProcessor.java` | UPDATE | Parse structured `inputs` JSON instead of wrapping `inputText` as single input |
| `backend-spring/src/main/java/com/ulticode/modules/submission/service/CodeExecutionHelper.java` | UPDATE | Add `typescript` to supported languages OR document why not |
| `console/src/views/problems/test/TestCaseView.vue` | UPDATE | Add expected output requirement for custom cases (optional) |
| `backend-spring/src/test/java/com/ulticode/common/util/SecurityUtilTest.java` | CREATE | Tests for anonymous user behavior |
| `backend-spring/src/test/java/com/ulticode/modules/submission/controller/ProblemSubmissionControllerTest.java` | UPDATE/CREATE | Tests for auth boundary on run/submit |

## NOT Building
- **Full TypeScript sandbox execution** — P3 只要求移除前端选项或 backend 支持。真正的 ts-node/tsc 沙箱集成超出范围。
- **Problem notes full CRUD** — 如果后端暂不做，只隐藏笔记按钮，不做完整笔记系统。
- **Bulk import/reorder/export test cases UI** — Admin API 实现核心 CRUD，批量/排序/导出可延后。
- **WebSocket real-time submission updates** — 已有 queue + polling 架构，不改 websocket 层。
- **Sandbox image build automation** — 只修复 volume 路径和错误码，不重建 Docker 镜像。

---

## Step-by-Step Tasks

### Task 1: Fix SecurityUtil.getCurrentUserId() [P0]
- **ACTION**: Update `SecurityUtil.getCurrentUserId()` to return `null` when principal is `anonymousUser`.
- **IMPLEMENT**: After `authentication.isAuthenticated()`, add `&& !"anonymousUser".equals(authentication.getPrincipal())` check.
- **MIRROR**: `SecurityUtil.isAuthenticated()` already does this — mirror the same check.
- **IMPORTS**: None (same class, uses `Authentication`).
- **GOTCHA**: `authentication.getPrincipal()` may return a `UserDetails` object for real users, not just a String. Use `authentication.getName()` for comparison, or check `getPrincipal() instanceof String`. The existing `isAuthenticated()` uses `"anonymousUser".equals(authentication.getPrincipal())` which works because `AnonymousAuthenticationToken` stores principal as `"anonymousUser"` String. If we use `getName()`, it also returns `"anonymousUser"`. Either is fine; match the existing `isAuthenticated()` pattern exactly.
- **VALIDATE**: Run `SecurityUtilTest` (to be created) — assert `getCurrentUserId()` returns `null` for anonymous auth, returns real ID for authenticated user.

### Task 2: Tighten SecurityConfig PUBLIC_ENDPOINTS [P0]
- **ACTION**: Replace `/problems/**` wildcard with explicit public GET paths. Keep `/problems`, `/problems/slug/{slug}`, `/problems/{id}`, `/problems/{id}/adjacent`, `/problems/random`.
- **IMPLEMENT**:
  ```java
  // Remove: "/problems/**"
  // Add explicit paths:
  "/problems",
  "/problems/{id:[0-9]+}",
  "/problems/slug/{slug}",
  "/problems/{id:[0-9]+}/adjacent",
  "/problems/random",
  ```
  Note: Spring Security `requestMatchers(String...)` supports path patterns with `{id:[0-9]+}` for numeric IDs. If wildcard is needed for slug, use `/problems/slug/**`.
- **MIRROR**: `SecurityConfig.java:40-79` existing array structure.
- **IMPORTS**: None.
- **GOTCHA**: `/problems` (list) and `/problems/slug/{slug}` (detail by slug) must remain public. `/problems/{id}` (detail by ID) must also remain public. Do NOT accidentally block the detail page. Spring's path matching is order-sensitive but here we just list patterns in the array.
- **VALIDATE**: Start backend, test:
  - `GET /problems/slug/two-sum` → 200 (no auth)
  - `POST /problems/1/submissions` → 401 (no auth)
  - `POST /problems/1/submissions/run` → 401 (no auth) — or decide if run should be public with stricter rate limit.

### Task 3: Update ProblemSubmissionController auth checks [P0]
- **ACTION**: Ensure `submitForProblem` and `runCode` properly reject anonymous users after SecurityUtil fix.
- **IMPLEMENT**: After SecurityUtil fix, `getCurrentUserId()` returns `null` for anonymous. The existing `if (userId == null)` checks will work. No code change needed in controller IF Task 1 is done correctly. However, verify that `runCode` should it allow anonymous? Product decision: **no**, run should also require auth. Keep current controller code as-is after SecurityUtil fix.
- **MIRROR**: Existing `null` check pattern in controller.
- **IMPORTS**: None.
- **GOTCHA**: If product later decides run should be public, add a separate permit rule in SecurityConfig and add `@PreAuthorize("permitAll()")` or remove the null check. For now, keep auth required.
- **VALIDATE**: Integration test: anonymous POST to `/problems/1/submissions/run` → 401.

### Task 4: Fix frontend content mapping [P2]
- **ACTION**: Update `mapProblemDetail` in `console/src/api/problem-detail.ts` to use `detail.content` as primary source for `content`.
- **IMPLEMENT**: Change line 127-128 from:
  ```typescript
  content: detail.summary ?? response.summary ?? "",
  summary: detail.summary ?? response.summary ?? "",
  ```
  to:
  ```typescript
  content: detail.content ?? detail.summary ?? response.summary ?? "",
  summary: detail.summary ?? response.summary ?? "",
  ```
- **MIRROR**: Existing fallback chain pattern in same file.
- **IMPORTS**: None.
- **GOTCHA**: `BackendProblemDetail` interface does not currently have a `content` field. Add `content?: string;` to `BackendProblemDetail` interface at line 24.
- **VALIDATE**: Load problem detail page for Two Sum. Verify full markdown content (including `## Test\nContent`) renders, not just `Test summary`.

### Task 5: Add real stats to ProblemDetailResponse [P2]
- **ACTION**: In `ProblemServiceImpl.buildDetailResponse`, replace hardcoded `submissionCount=0L`, `solutionCount=0L`, `tags=emptyList()` with real queries.
- **IMPLEMENT**:
  ```java
  // submissionCount: count from submissions table
  Long submissionCount = submissionMapper.selectCount(
      new LambdaQueryWrapper<Submission>().eq(Submission::getProblemId, problem.getId()));
  response.setSubmissionCount(submissionCount);

  // solutionCount: count from solutions table
  Long solutionCount = solutionMapper.selectCount(
      new LambdaQueryWrapper<Solution>().eq(Solution::getProblemId, problem.getId()));
  response.setSolutionCount(solutionCount);

  // tags: query problem_tag_relations joined with problem_tags
  List<ProblemTagVO> tags = problemTagRelationMapper.selectTagsByProblemId(problem.getId());
  response.setTags(tags);
  ```
  Note: If mappers don't have these methods, add them or use existing ones. Check `ProblemTagRelationMapper` and `SubmissionMapper`.
- **MIRROR**: `ProblemServiceImpl.java:313-315` current hardcoded lines; `buildLanguages` pattern for query-then-set.
- **IMPORTS**: `SubmissionMapper`, `SolutionMapper`, `ProblemTagRelationMapper` (may already be injected).
- **GOTCHA**: `ProblemServiceImpl` may not have `submissionMapper` or `solutionMapper` injected. Check constructor and add if missing. For tags, there may already be a method — search for `selectTagsByProblemId` or similar.
- **VALIDATE**: `GET /problems/slug/two-sum` should return non-zero `submission_count` if submissions exist, and actual `tags` array.

### Task 6: Add interactions to public detail response [P2]
- **ACTION**: Include `likes`, `dislikes`, `favorites` counts in `ProblemDetailResponse`.
- **IMPLEMENT**:
  1. Add `interactions` field to `ProblemDetailResponse`:
     ```java
     private InteractionData interactions;
     ```
     with inner class:
     ```java
     @Data @JsonInclude(JsonInclude.Include.NON_NULL)
     public static class InteractionData {
         private Integer likes;
         private Integer dislikes;
         private Integer favorites;
         @JsonProperty("viewer_reaction")
         private String viewerReaction; // null for anonymous
     }
     ```
  2. In `ProblemServiceImpl.buildDetailResponse`, query `problem_details` table for `likes`, `dislikes`, and compute favorites count from `bookmark` table or `interactions` JSON. The `problem_details` table has `likes`, `dislikes`, `interactions` JSON column. Read `likes` and `dislikes` directly from `ProblemDetail` entity. For viewer reaction, only set if authenticated.
  3. Frontend `problem-detail.ts`: map `response.interactions` into `ProblemDetail.interactions`.
- **MIRROR**: `ProblemDetailResponse.DetailData` nested class pattern.
- **IMPORTS**: `ProblemDetailMapper` (already used in `buildDetailData`).
- **GOTCHA**: `interactions` JSON column in `problem_details` may be null. Handle null safely. The `likes`/`dislikes` are denormalized integer columns, not inside JSON.
- **VALIDATE**: Response includes `interactions: { likes: 54300, dislikes: 1800, favorites: 0 }` for Two Sum.

### Task 7: Make edge-operations counts publicly readable [P2]
- **ACTION**: Update the `/edge-operations/{targetType}/{targetId}` endpoint (or create a new public counts endpoint) to allow anonymous access for read-only counts.
- **IMPLEMENT**: Find `EdgeOperationController` or similar. The endpoint should return counts without requiring auth. If it currently requires auth, add a separate `GET /edge-operations/{targetType}/{targetId}/counts` that is public, or make the existing GET allow anonymous and return `viewer: null` when not authenticated.
- **MIRROR**: Existing controller pattern with `SecurityUtil.isAuthenticated()` guard.
- **IMPORTS**: Check `EdgeOperationController` or `InteractionController`.
- **GOTCHA**: The endpoint name may not be exactly `edge-operations`. Search the codebase for `edge` or `interaction` controllers. In the analysis it references `GET /edge-operations/PROBLEM/1`. Find the actual controller.
- **VALIDATE**: `GET /edge-operations/PROBLEM/1` without auth → 200 with counts, `viewer` null.

### Task 8: Create test_cases Flyway migration [P1]
- **ACTION**: Create migration `V{next}__create_test_cases.sql` that creates the `test_cases` table matching the `TestCase` entity.
- **IMPLEMENT**:
  ```sql
  CREATE TABLE `test_cases` (
    `id` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
    `problem_id` bigint NOT NULL,
    `is_sample` tinyint(1) NOT NULL DEFAULT '0',
    `is_hidden` tinyint(1) NOT NULL DEFAULT '0',
    `test_order` int NOT NULL DEFAULT '0',
    `input_text` text COLLATE utf8mb4_unicode_ci NOT NULL,
    `output_text` text COLLATE utf8mb4_unicode_ci NOT NULL,
    `explanation` text COLLATE utf8mb4_unicode_ci,
    `constraints` text COLLATE utf8mb4_unicode_ci,
    `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    KEY `test_cases_problem_id_fkey` (`problem_id`),
    CONSTRAINT `test_cases_problem_id_fkey` FOREIGN KEY (`problem_id`) REFERENCES `problems` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
  );
  ```
- **MIRROR**: `V2__problem_schema.sql` table creation style (same collation, same timestamp defaults).
- **IMPORTS**: N/A (SQL file).
- **GOTCHA**: Use the next available version number. Check `db-manager/migrations/` for the highest V number. The entity has `constraints` field — decide if it belongs in SQL. The entity uses `@TableName("test_cases")` which means the table MUST be named `test_cases`.
- **VALIDATE**: Run `db-manager info` then `db-manager migrate --dry-run` to verify.

### Task 9: Seed test_cases for existing problems [P1]
- **ACTION**: Create migration `V{next+1}__seed_test_cases.sql` that copies `problem_examples` data into `test_cases` as `is_sample=true`, and adds minimal hidden cases for existing problems.
- **IMPLEMENT**: For each problem, insert the existing `problem_examples` rows into `test_cases` with `is_sample=1, is_hidden=0`. For at least Two Sum (problem_id=1), add one hidden test case:
  ```sql
  INSERT INTO `test_cases` (`id`, `problem_id`, `is_sample`, `is_hidden`, `test_order`, `input_text`, `output_text`, `explanation`) VALUES
  ('tc-two-sum-hidden-1', 1, 0, 1, 3, 'nums = [1,2,3,4,5], target = 8', '[2,4]', 'Hidden case: 3+5=8');
  ```
- **MIRROR**: `V2__problem_schema.sql` INSERT style.
- **GOTCHA**: `input_text` format must match what the sandbox wrapper expects. The worker currently wraps `inputText` as a single input value. This is a known issue (Task 11). For now, keep `input_text` in the same string format as `problem_examples`.
- **VALIDATE**: After migrate, query `SELECT * FROM test_cases WHERE problem_id=1;` — should return 4 rows (3 sample + 1 hidden).

### Task 10: Implement admin test-cases CRUD controller [P1]
- **ACTION**: Create `AdminTestCaseController` under `modules/admin/controller/` with endpoints matching management frontend's expectations.
- **IMPLEMENT**:
  ```java
  @RestController
  @RequestMapping("/admin/problems/{problemId}/test-cases")
  @RequiredArgsConstructor
  @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
  public class AdminTestCaseController {
      private final AdminTestCaseService adminTestCaseService;

      @GetMapping
      public Result<List<TestCaseVO>> list(@PathVariable Long problemId) { ... }

      @PostMapping
      public Result<TestCaseVO> create(@PathVariable Long problemId, @RequestBody CreateTestCaseDTO dto) { ... }

      @PutMapping("/{caseId}")
      public Result<TestCaseVO> update(@PathVariable Long problemId, @PathVariable String caseId, @RequestBody UpdateTestCaseDTO dto) { ... }

      @DeleteMapping("/{caseId}")
      public Result<Void> delete(@PathVariable Long problemId, @PathVariable String caseId) { ... }
  }
  ```
- **MIRROR**: `AdminProblemController.java` pattern (same package, same `@PreAuthorize`).
- **IMPORTS**: `TestCaseMapper`, `TestCase` entity, existing `Result` class.
- **GOTCHA**: Management frontend already has API types in `management/src/api/admin/test-cases.ts`. Align DTO field names with those types. Check that file for expected request/response shapes.
- **VALIDATE**: Management frontend can list, create, update, delete test cases without 404.

### Task 11: Fix JudgeWorkerProcessor structured input parsing [P1]
- **ACTION**: In `buildRunSubmissionDTO`, parse `inputs` JSON column instead of wrapping entire `inputText` as a single `input` parameter.
- **IMPLEMENT**:
  ```java
  private RunSubmissionDTO buildRunSubmissionDTO(JudgeJob job, List<TestCase> testCases) {
      RunSubmissionDTO runDto = new RunSubmissionDTO();
      runDto.setLanguage(job.getLanguage());
      runDto.setCode(job.getCode());
      runDto.setTestCases(testCases.stream().map(tc -> {
          RunSubmissionDTO.RunTestCase rtc = new RunSubmissionDTO.RunTestCase();
          rtc.setId(String.valueOf(tc.getId()));
          rtc.setLabel("Case " + tc.getTestOrder());
          rtc.setOutput(tc.getOutputText());

          // Parse structured inputs from JSON if available
          List<RunSubmissionDTO.RunInput> runInputs = new ArrayList<>();
          if (tc.getInputs() != null && !tc.getInputs().isBlank()) {
              try {
                  List<InputData> inputs = objectMapper.readValue(tc.getInputs(), new TypeReference<List<InputData>>() {});
                  for (int i = 0; i < inputs.size(); i++) {
                      InputData id = inputs.get(i);
                      RunSubmissionDTO.RunInput ri = new RunSubmissionDTO.RunInput();
                      ri.setId(String.valueOf(i));
                      ri.setLabel(id.getName());
                      ri.setName(id.getName());
                      ri.setValue(id.getValue());
                      runInputs.add(ri);
                  }
              } catch (JsonProcessingException e) {
                  log.warn("Failed to parse inputs JSON for test case {}, falling back to inputText", tc.getId());
              }
          }
          // Fallback: if no structured inputs, wrap inputText as single input
          if (runInputs.isEmpty() && tc.getInputText() != null) {
              RunSubmissionDTO.RunInput ri = new RunSubmissionDTO.RunInput();
              ri.setId("0");
              ri.setLabel("input");
              ri.setName("input");
              ri.setValue(tc.getInputText());
              runInputs.add(ri);
          }
          rtc.setInputs(runInputs);
          return rtc;
      }).toList());
      return runDto;
  }
  ```
- **MIRROR**: `JudgeWorkerProcessor.java:276-290` current implementation.
- **IMPORTS**: `ObjectMapper`, `InputData` (from `ProblemDetailResponse` or create new inner class).
- **GOTCHA**: `TestCase` entity currently does NOT have an `inputs` field. You must add it:
  ```java
  @TableField("inputs")
  private String inputs; // JSON string
  ```
  And add the column to the Flyway migration.
- **VALIDATE**: Worker test: mock `testCaseMapper.findByProblemIdOrderByOrder` returning a case with `inputs='[{"name":"nums","value":"[1,2]"},{"name":"target","value":"3"}]'`. Assert `buildRunSubmissionDTO` produces two `RunInput` objects.

### Task 12: Fix empty test cases handling in CodeExecutionService [P1/P3]
- **ACTION**: When `testCases` is null or empty, throw `BusinessException(BAD_REQUEST)` instead of returning `Accepted`.
- **IMPLEMENT**: In `CodeExecutionService.execute`, replace:
  ```java
  if (testCases == null || testCases.isEmpty()) {
      return helper.emptyResult(problemId, userId);
  }
  ```
  with:
  ```java
  if (testCases == null || testCases.isEmpty()) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "No test cases provided for execution");
  }
  ```
  Also update `emptyResult` to return `System Error` verdict instead of `Accepted` for cases where it is still used (e.g., fallback).
- **MIRROR**: Existing `BusinessException` throw pattern in controller.
- **IMPORTS**: `ErrorCode.BAD_REQUEST`.
- **GOTCHA**: Ensure frontend handles this gracefully. The run button should not be clickable without test cases, but if it is, the error message should display.
- **VALIDATE**: Unit test: `execute_emptyTestCases_throwsBadRequest()`.

### Task 13: Unify verdict priority in CodeExecutionService [P3]
- **ACTION**: Replace simple `passedCases == totalCases ? "Accepted" : "Wrong Answer"` with priority-based verdict matching `JudgeWorkerProcessor.determineVerdict()`.
- **IMPLEMENT**: Extract the priority logic from `JudgeWorkerProcessor` into a shared utility (e.g., `VerdictUtil`), or copy the priority map into `CodeExecutionService`. Use:
  ```java
  String verdict = determineVerdict(results);
  ```
  where `determineVerdict` checks case statuses in priority order: Runtime Error > Compile Error > Time Limit Exceeded > Memory Limit Exceeded > Wrong Answer > Presentation Error > Accepted.
- **MIRROR**: `JudgeWorkerProcessor.java:229-246` `determineVerdict` method.
- **IMPORTS**: Extract to `com.ulticode.common.util.VerdictUtil` or inline.
- **GOTCHA**: Compile Error typically happens at the sandbox level before cases run. If `results` is empty due to compile error, the sandbox service should return a single case with status `Compile Error`. Ensure `SandboxServiceImpl` handles this.
- **VALIDATE**: Unit test with cases: `[RE, WA]` → verdict `Runtime Error`; `[WA, TLE]` → `Time Limit Exceeded`; `[WA, WA]` → `Wrong Answer`; `[AC, AC]` → `Accepted`.

### Task 14: Fix frontend errorMessage field mapping [P3]
- **ACTION**: Frontend `ProblemRunResult` should read both `errorMessage` (backend camelCase) and `error_message` (legacy snake_case).
- **IMPLEMENT**: In `console/src/types/test-results.ts`, change:
  ```typescript
  error_message?: string;
  ```
  to:
  ```typescript
  errorMessage?: string;
  error_message?: string;
  ```
  In `TestResultsView.vue`, change references from `props.runResult.error_message` to:
  ```typescript
  const errorText = computed(() => props.runResult?.errorMessage ?? props.runResult?.error_message ?? '');
  ```
  Update template to use `errorText`.
- **MIRROR**: Existing `??` fallback pattern in TypeScript.
- **GOTCHA**: Check if any other components reference `error_message`.
- **VALIDATE**: Run a submission that produces Compile Error. Verify error details display in results panel.

### Task 15: Fix TestResultsView verdict labels [P3]
- **ACTION**: Replace `t("problem.status.solved")` / `t("problem.status.attempted")` with dedicated run verdict translation keys.
- **IMPLEMENT**: Add new i18n keys (e.g., `problem.verdict.accepted`, `problem.verdict.wrongAnswer`, etc.) and map in `verdictLabel` computed:
  ```typescript
  const VERDICT_LABELS: Record<string, string> = {
    'Accepted': t('problem.verdict.accepted'),
    'Wrong Answer': t('problem.verdict.wrongAnswer'),
    'Runtime Error': t('problem.verdict.runtimeError'),
    'Compile Error': t('problem.verdict.compileError'),
    'Time Limit Exceeded': t('problem.verdict.timeLimitExceeded'),
    'Memory Limit Exceeded': t('problem.verdict.memoryLimitExceeded'),
    'System Error': t('problem.verdict.systemError'),
  };
  ```
- **MIRROR**: Existing `verdictClass` switch pattern.
- **IMPORTS**: Check i18n locale files under `console/src/i18n/locales/`.
- **GOTCHA**: Do not remove existing `problem.status.*` keys — they are used elsewhere. Add new keys under `problem.verdict.*`.
- **VALIDATE**: Run code with Wrong Answer → panel shows localized "Wrong Answer" label, not "Attempted".

### Task 16: Remove unsupported TypeScript from language options [P3]
- **ACTION**: Filter out `typescript` from `buildLanguages` in `ProblemServiceImpl`, or from frontend `mapLanguages`.
- **IMPLEMENT**: Short-term fix in backend `ProblemServiceImpl.buildLanguages`:
  ```java
  List<LanguageData> languages = buildLanguages(problem.getId());
  // Filter out unsupported languages
  Set<String> supported = CodeExecutionHelper.SUPPORTED_LANGUAGES;
  languages = languages.stream()
      .filter(l -> supported.contains(l.getValue().toLowerCase()))
      .toList();
  ```
  This ensures API never returns `typescript` even if DB has it.
- **MIRROR**: `buildLanguages` existing query-then-filter pattern.
- **IMPORTS**: `CodeExecutionHelper`.
- **GOTCHA**: If `buildLanguages` returns `LanguageData` objects where `value` might be `mysql`, `shell`, etc., those will also be filtered out. The supported set is `javascript, python, java, c, cpp`. This is correct behavior — only return languages the sandbox can execute.
- **VALIDATE**: `GET /problems/slug/two-sum` returns only `javascript`, no `typescript`.

### Task 17: Fix or hide problem notes [P2]
- **ACTION**: Either implement `GET/POST /problems/{problemId}/note` backend endpoints, or hide the notes drawer button in frontend.
- **IMPLEMENT** (hide option — lower risk):
  In `console/src/views/problems/ProblemDetailView.vue`, find the notes drawer trigger and add `v-if="isAuthenticated"` or remove it entirely.
- **MIRROR**: N/A.
- **GOTCHA**: Check if notes button exists in the layout. Search for `ProblemNotesDrawer` or notes-related components.
- **VALIDATE**: Notes button not visible on problem detail page.

---

## Testing Strategy

### Unit Tests

| Test | Input | Expected Output | Edge Case? |
|---|---|---|---|
| `getCurrentUserId_anonymous_returnsNull` | Anonymous auth context | `null` | Yes |
| `getCurrentUserId_authenticated_returnsName` | JWT auth context | User ID | No |
| `securityFilterChain_problemsPost_requiresAuth` | `POST /problems/1/submissions` | 401 | Yes |
| `securityFilterChain_problemsGet_permitsAll` | `GET /problems/slug/two-sum` | 200 | No |
| `submitForProblem_nullUserId_throws401` | `null` userId | `BusinessException(UNAUTHORIZED)` | Yes |
| `mapProblemDetail_usesContentField` | `detail.content="Full"`, `detail.summary="Short"` | `content="Full"` | No |
| `buildDetailResponse_realStats` | Problem with 5 submissions, 2 solutions, 3 tags | Correct counts and tags | No |
| `determineVerdict_priority` | Cases `[WA, RE, TLE]` | `Runtime Error` | Yes |
| `execute_emptyTestCases_throwsBadRequest` | Empty `testCases` list | `BusinessException(BAD_REQUEST)` | Yes |
| `buildRunSubmissionDTO_structuredInputs` | `inputs` JSON with 2 params | 2 `RunInput` objects | Yes |

### Edge Cases Checklist
- [ ] Anonymous access to all previously public GET endpoints still works
- [ ] Anonymous access to POST /problems/{id}/submissions returns 401
- [ ] Anonymous access to POST /problems/{id}/submissions/run returns 401
- [ ] Problem with no `detail.content` falls back to `detail.summary`
- [ ] Problem with no `problem_details` row still returns basic fields
- [ ] `test_cases` table migration is backward compatible (new table, no existing code depends on it yet)
- [ ] Judge worker handles `test_cases` with null `inputs` JSON (fallback to `inputText`)
- [ ] Admin test case CRUD validates `problemId` exists
- [ ] Frontend with legacy `error_message` still works (dual field support)

---

## Validation Commands

### Static Analysis
```bash
# Backend compile
cd backend-spring && ./mvnw compile -q
```
EXPECT: Zero compilation errors

### Unit Tests
```bash
# Backend unit tests (excludes IT)
cd backend-spring && ./mvnw test -q
```
EXPECT: All tests pass

### Integration Tests
```bash
# Backend integration tests
cd backend-spring && ./mvnw verify -Pci -q
```
EXPECT: All IT tests pass

### Frontend Type Check
```bash
cd console && pnpm type-check
```
EXPECT: Zero type errors

### Frontend Lint
```bash
cd console && pnpm lint
```
EXPECT: Zero lint errors

### Database Validation
```bash
cd db-manager && db-manager validate
```
EXPECT: Schema up to date, no migration errors

### Manual Validation
- [ ] Load `/problems/two-sum` in browser — verify full markdown content renders
- [ ] Verify language dropdown only shows JavaScript (and other supported languages)
- [ ] Click Run with sample cases — verify results display correctly
- [ ] Submit code — verify submission is queued and judge worker processes it
- [ ] Check admin panel test cases management loads without 404
- [ ] Log out and verify `POST /problems/1/submissions` returns 401
- [ ] Log out and verify `GET /problems/slug/two-sum` still returns 200

---

## Acceptance Criteria
- [ ] All P0 security tasks completed (SecurityUtil, SecurityConfig, controller auth)
- [ ] All P1 judge pipeline tasks completed (migration, seed, admin API, worker input)
- [ ] All P2 detail experience tasks completed (content mapping, stats, interactions, notes)
- [ ] All P3 language/result tasks completed (language filter, verdict priority, errorMessage)
- [ ] All validation commands pass
- [ ] Backend tests written and passing for new/changed logic
- [ ] No type errors in console frontend
- [ ] No new lint errors
- [ ] Database migrations validate cleanly

## Completion Checklist
- [ ] Code follows discovered patterns (naming, error handling, logging)
- [ ] Error handling matches codebase style (`BusinessException` with `ErrorCode`)
- [ ] Logging follows conventions (parameterized SLF4J, no code content in logs)
- [ ] Tests follow test patterns (JUnit 5 + Mockito + AssertJ, `@DisplayName`)
- [ ] No hardcoded values (use constants/config for thresholds)
- [ ] Documentation updated (migration README if applicable)
- [ ] No unnecessary scope additions (NOT Building list respected)
- [ ] Self-contained — no questions needed during implementation

## Risks
| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Tightening `/problems/**` breaks unintended public endpoints | Medium | High | List ALL `/problems/*` endpoints in codebase before editing SecurityConfig; test each one |
| `test_cases` migration conflicts with existing manual table | Low | High | Check `db-manager info` first; use `baseline` if needed |
| JudgeWorkerProcessor input parsing change breaks existing sandbox contract | Medium | High | Keep `inputText` fallback; add unit tests for both JSON and fallback paths |
| Frontend `content` mapping change breaks problems with only `summary` | Low | Medium | Fallback chain: `content ?? summary ?? ""` ensures compatibility |
| Removing TypeScript language angers users who started code in TS | Low | Low | TS was already broken at runtime; removing it prevents false promise |
| `submissionCount`/`solutionCount` queries add latency to detail page | Medium | Medium | Add caching or denormalized counters in future; for now accept query cost |

## Notes
- The analysis doc is the authoritative source of truth for specific line numbers and API behavior. Refer back to it when implementation details are unclear.
- `SecurityUtil.isAuthenticated()` already correctly excludes `anonymousUser` — the bug is only in `getCurrentUserId()`. Fix that method and most auth issues resolve automatically.
- `test_cases` table does not exist in any migration as of V2. The entity `TestCase.java` exists and points to `@TableName("test_cases")`, confirming the intended table name. Creating the migration unblocks the worker.
- The management frontend (`management/src/api/admin/test-cases.ts`) already has typed API functions for test case CRUD. The backend controller should align with those types.
- For sandbox volume path fix (`$(pwd)` in `SandboxServiceImpl.java`), check if `SandboxServiceImpl` uses `System.getProperty("user.dir")` or literal `"$(pwd)"`. If literal, replace with `Paths.get("").toAbsolutePath()` or configured path.
