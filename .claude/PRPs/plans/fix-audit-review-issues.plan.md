# Plan: Fix Audit Review Issues

## Summary

Fix all HIGH, MEDIUM, and LOW severity issues identified in the audit-logging-integration code review. The core work involves eliminating N+1 queries in three admin service VOs, adding defensive null checks, replacing looped single-row inserts with batch inserts, tightening exception handling in bulk operations, and correcting minor code-quality issues.

## User Story

As a developer maintaining the UltiCode admin panel,
I want the audit-logging integration to be free of N+1 queries, null-safety bugs, and inefficient bulk operations,
So that the admin APIs remain performant and reliable under production load.

## Problem → Solution

**Current state**: Admin services fetch related entities row-by-row inside stream mappers, perform single-row inserts in loops, silently ignore parse errors, and use overly broad catch blocks.

**Desired state**: Batch pre-loading of related data, `@Insert`+`<foreach>` batch inserts, explicit validation exceptions, and narrowed exception handling.

## Metadata
- **Complexity**: Large
- **Source PRD**: `.claude/reviews/audit-logging-integration-review.md`
- **PRD Phase**: standalone
- **Estimated Files**: 12+ (9 services + 3 mappers + tests)

---

## UX Design

N/A — internal backend change, no user-facing UX transformation.

---

## Mandatory Reading

Files that MUST be read before implementing:

| Priority | File | Lines | Why |
|---|---|---|---|
| P0 | `AdminSubmissionServiceImpl.java` | 118-156 | Batch-loading pattern (WR-05) to mirror |
| P0 | `AdminForumServiceImpl.java` | 320-378 | N+1 hotspot: comment/vote counts per post |
| P0 | `AdminCommentServiceImpl.java` | 146-423 | N+1 hotspot: user+parent per comment |
| P0 | `AdminSolutionServiceImpl.java` | 270-327 | N+1 hotspot: user+problem per solution |
| P1 | `ForumCommentMapper.java` | 72-79 | Existing `countByPostId` signature |
| P1 | `EdgeOperationMapper.java` | 27-31 | Existing `countByTargetAndOperation` signature |
| P1 | `ContestProblemMapper.java` | all | Target for batch-insert method |
| P1 | `NotificationMapper.java` | all | Target for batch-insert method |
| P2 | `ErrorCode.java` | all | Confirm `VALIDATION_FAILED` exists |

## External Documentation

| Topic | Source | Key Takeaway |
|---|---|---|
| MyBatis-Plus BaseMapper | https://baomidou.com/ | `selectBatchIds(Collection<? extends Serializable>)` is inherited from `BaseMapper`; `@Insert` supports `<script>` with `<foreach>` for batch inserts without XML |
| MyBatis-Plus IService | https://baomidou.com/ | Project does NOT use `IService`/`ServiceImpl`; all services inject mappers directly. Do NOT introduce `IService` pattern. |

---

## Patterns to Mirror

### BATCH_LOADING_PATTERN
// SOURCE: AdminSubmissionServiceImpl.java:118-156
```java
// Batch-load users and problems to avoid N+1 queries (WR-05)
Map<String, User> userMap = new HashMap<>();
Map<Long, Problem> problemMap = new HashMap<>();
if (!result.getRecords().isEmpty()) {
    Set<String> userIds = result.getRecords().stream()
            .map(Submission::getUserId)
            .collect(Collectors.toSet());
    Set<Long> problemIds = result.getRecords().stream()
            .map(Submission::getProblemId)
            .collect(Collectors.toSet());

    if (!userIds.isEmpty()) {
        userMap = userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
    }
    if (!problemIds.isEmpty()) {
        problemMap = problemMapper.selectBatchIds(problemIds).stream()
                .collect(Collectors.toMap(Problem::getId, p -> p));
    }
}

// Enrich with batch-loaded maps
Map<String, User> finalUserMap = userMap;
Map<Long, Problem> finalProblemMap = problemMap;
List<AdminSubmissionVO> vos = result.getRecords().stream()
        .map(s -> toAdminVO(s, finalUserMap, finalProblemMap))
        .collect(Collectors.toList());
```

### BATCH_INSERT_ANNOTATION_PATTERN
// SOURCE: none in project — new pattern, consistent with existing `@Select`/`@Insert` style
```java
@Insert("<script>INSERT INTO contest_problems " +
        "(contest_id, problem_id, problem_index, score, base_score, solved_count, submission_count) VALUES " +
        "<foreach collection='list' item='item' separator=','>" +
        "(#{item.contestId}, #{item.problemId}, #{item.problemIndex}, #{item.score}, #{item.baseScore}, #{item.solvedCount}, #{item.submissionCount})" +
        "</foreach></script>")
int batchInsert(@Param("list") List<ContestProblem> list);
```

### ERROR_HANDLING
// SOURCE: AdminSubmissionServiceImpl.java:348-352
```java
if (ids.size() > 50) {
    throw new BusinessException(ErrorCode.VALIDATION_FAILED,
        "Batch size exceeds maximum of 50");
}
```

### LOGGING_PATTERN
// SOURCE: AdminContestServiceImpl.java:158
```java
log.info("Admin created contest: {} by user {}", contest.getId(), userId);
```

### SERVICE_PATTERN
// SOURCE: AdminContestServiceImpl.java:40-50
```java
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminContestServiceImpl implements AdminContestService {
    private final ContestMapper contestMapper;
    // ... other final fields
}
```

### TEST_STRUCTURE
// SOURCE: AdminForumServiceImplTest.java:34-53
```java
@ExtendWith(MockitoExtension.class)
class AdminForumServiceImplTest {
    @Mock private ForumPostMapper forumPostMapper;
    @Mock private AuditHelper auditHelper;
    @InjectMocks private AdminForumServiceImpl adminForumService;

    @BeforeEach void setUp() { /* init testPost */ }
}
```

---

## Files to Change

| File | Action | Justification |
|---|---|---|
| `AdminForumServiceImpl.java` | UPDATE | Fix N+1: batch-load comment counts and vote counts |
| `AdminCommentServiceImpl.java` | UPDATE | Fix N+1: batch-load users and parent entities; rename misleading method |
| `AdminSolutionServiceImpl.java` | UPDATE | Fix N+1: batch-load users and problems in list view |
| `AdminProblemListServiceImpl.java` | UPDATE | Add null check for `dto.getProblems()` |
| `AdminContestServiceImpl.java` | UPDATE | Replace looped inserts with batch insert; fix fully-qualified import |
| `AdminNotificationServiceImpl.java` | UPDATE | Replace looped inserts with batch insert; fix dedup key |
| `AdminUserServiceImpl.java` | UPDATE | Throw on date parse failure; narrow catch blocks |
| `AdminTagServiceImpl.java` | UPDATE | Fix fully-qualified class names |
| `ForumCommentMapper.java` | UPDATE | Add `countByPostIds` batch method |
| `EdgeOperationMapper.java` | UPDATE | Add `countByTargetsAndOperation` batch method |
| `ContestProblemMapper.java` | UPDATE | Add `batchInsert` method |
| `NotificationMapper.java` | UPDATE | Add `batchInsert` method |
| `AdminForumServiceImplTest.java` | UPDATE | Adjust mocks if `toAdminVO` signature changes |
| `AdminSubmissionServiceImplTest.java` | UPDATE | Ensure still passes after any service changes |

## NOT Building

- Do NOT introduce `IService` / `ServiceImpl` pattern (project uses mapper injection only)
- Do NOT create XML mapper files (project uses annotation-only mappers)
- Do NOT refactor unrelated service logic beyond the review findings
- Do NOT add caching layers
- Do NOT change database schema

---

## Step-by-Step Tasks

### Task 1: Add batch-count methods to ForumCommentMapper and EdgeOperationMapper
- **ACTION**: Add `@Select` annotated methods that accept `List<String>` and use `<foreach>` `IN` clause.
- **IMPLEMENT**:
  - In `ForumCommentMapper.java`, add:
    ```java
    @Select("<script>SELECT post_id, COUNT(*) as cnt FROM forum_comments WHERE post_id IN " +
            "<foreach collection='postIds' item='id' open='(' separator=',' close=')'>#{id}</foreach> " +
            "AND is_deleted = 0 GROUP BY post_id</script>")
    List<Map<String, Object>> countByPostIds(@Param("postIds") List<String> postIds);
    ```
  - In `EdgeOperationMapper.java`, add:
    ```java
    @Select("<script>SELECT target_id, COUNT(*) as cnt FROM edge_operations WHERE target_id IN " +
            "<foreach collection='targetIds' item='id' open='(' separator=',' close=')'>#{id}</foreach> " +
            "AND target_type = #{targetType} AND operation_type = #{operationType} GROUP BY target_id</script>")
    List<Map<String, Object>> countByTargetsAndOperation(@Param("targetIds") List<String> targetIds,
                                                          @Param("targetType") String targetType,
                                                          @Param("operationType") String operationType);
    ```
- **MIRROR**: Existing `@Select` style in both mappers (single-parameter versions already present)
- **IMPORTS**: `java.util.List`, `java.util.Map`, `org.apache.ibatis.annotations.Param`
- **GOTCHA**: MyBatis-Plus annotation mappers with `<script>` must wrap the entire SQL in `<script>...</script>` when using dynamic tags like `<foreach>`
- **VALIDATE**: `./mvnw compile` passes

### Task 2: Fix N+1 in AdminForumServiceImpl.toAdminVO
- **ACTION**: Pre-compute comment counts, upvotes, and downvotes with batch queries before the stream mapping.
- **IMPLEMENT**:
  - In `getPosts()`, after `forumPostMapper.selectPage(...)`:
    1. Collect all `postId`s from `result.getRecords()`
    2. Call `forumCommentMapper.countByPostIds(postIds)` → build `Map<String, Long> commentCountMap`
    3. Call `edgeOperationMapper.countByTargetsAndOperation(postIds, "FORUM_POST", "VOTE_UP")` → build `Map<String, Integer> upvoteMap`
    4. Call `edgeOperationMapper.countByTargetsAndOperation(postIds, "FORUM_POST", "VOTE_DOWN")` → build `Map<String, Integer> downvoteMap`
  - Pass these three maps into `toAdminVO(ForumPost, Map, Map, Map)` or look them up via `final` local variables inside the stream
  - Remove the individual `countByPostId` and `countByTargetAndOperation` calls from `toAdminVO`
- **MIRROR**: BATCH_LOADING_PATTERN from `AdminSubmissionServiceImpl.java:118-156`
- **IMPORTS**: `java.util.Map`, `java.util.HashMap`, `java.util.stream.Collectors`
- **GOTCHA**: Ensure `toAdminVO` overloads remain compatible — create a new private overload for list view that accepts the maps, keep the old one for `getPost` / `toAdminVOWithDetails` if needed
- **VALIDATE**: `AdminForumServiceImplTest` still passes; if test mocks `countByPostId`, update to mock `countByPostIds`

### Task 3: Fix N+1 in AdminCommentServiceImpl
- **ACTION**: Batch-load users and parent entities (ForumPost / Solution) before VO mapping in `getForumComments` and `getSolutionComments`.
- **IMPLEMENT**:
  - In `getForumComments()`:
    1. After `forumCommentMapper.selectPage(...)`, collect `authorId`s and `postId`s
    2. `userMapper.selectBatchIds(authorIds)` → `Map<String, User>`
    3. `forumPostMapper.selectBatchIds(postIds)` → `Map<String, ForumPost>`
    4. Pass both maps into a new `forumToAdminVO(ForumComment, Map<String, User>, Map<String, ForumPost>)`
  - In `getSolutionComments()`: same pattern with `solutionMapper.selectBatchIds(solutionIds)`
  - Rename `getAllComments` → `getForumCommentsAsFallback` (or implement merged pagination; renaming is the pragmatic fix)
- **MIRROR**: BATCH_LOADING_PATTERN from `AdminSubmissionServiceImpl.java:118-156`
- **IMPORTS**: `java.util.Set`, `java.util.Map`, `java.util.HashMap`, `java.util.stream.Collectors`
- **GOTCHA**: `selectBatchIds` requires `Collection<? extends Serializable>`; `String` qualifies
- **VALIDATE**: `./mvnw test -Dtest=AdminCommentServiceImplTest` (if exists; otherwise verify compilation)

### Task 4: Fix N+1 in AdminSolutionServiceImpl
- **ACTION**: Batch-load users and problems in `getSolutions` before stream mapping.
- **IMPLEMENT**:
  - After `solutionMapper.selectPage(...)`:
    1. Collect `userId`s (`Set<String>`) and `problemId`s (`Set<Long>`)
    2. `userMapper.selectBatchIds(userIds)` → `Map<String, User>`
    3. `problemMapper.selectBatchIds(problemIds)` → `Map<Long, Problem>`
  - Create a new `toAdminVO(Solution, Map<String, User>, Map<Long, Problem>)` overload for list view
  - The existing `toAdminVO(Solution)` can remain for `getSolution` / `toAdminVOWithDetails`
- **MIRROR**: BATCH_LOADING_PATTERN from `AdminSubmissionServiceImpl.java:118-156`
- **IMPORTS**: same as Task 3
- **GOTCHA**: `AdminSolutionVO` already has nested `AuthorInfo` and `ProblemInfo` — reuse existing mapping logic inside the new overload
- **VALIDATE**: `./mvnw compile` passes

### Task 5: Add null check in AdminProblemListServiceImpl.updateListProblems
- **ACTION**: Validate `dto.getProblems()` before iterating.
- **IMPLEMENT**:
  ```java
  if (dto.getProblems() == null) {
      throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Problems list is required");
  }
  ```
  Insert this immediately before the existing `for` loop at line 183.
- **MIRROR**: ERROR_HANDLING from `AdminSubmissionServiceImpl.java:348-352`
- **IMPORTS**: none new needed
- **GOTCHA**: Do NOT change the DTO itself; this is a service-level guard
- **VALIDATE**: `./mvnw compile` passes

### Task 6: Add batchInsert to ContestProblemMapper and NotificationMapper
- **ACTION**: Add `@Insert`+`<foreach>` batch insert methods to both mappers.
- **IMPLEMENT**:
  - `ContestProblemMapper.java`:
    ```java
    @Insert("<script>INSERT INTO contest_problems " +
            "(id, contest_id, problem_id, problem_index, score, base_score, solved_count, submission_count) VALUES " +
            "<foreach collection='list' item='item' separator=','>" +
            "(#{item.id}, #{item.contestId}, #{item.problemId}, #{item.problemIndex}, #{item.score}, #{item.baseScore}, #{item.solvedCount}, #{item.submissionCount})" +
            "</foreach></script>")
    int batchInsert(@Param("list") List<ContestProblem> list);
    ```
  - `NotificationMapper.java`:
    ```java
    @Insert("<script>INSERT INTO notifications " +
            "(id, user_id, type, category, title, body, link, metadata, is_read, read_at, created_at) VALUES " +
            "<foreach collection='list' item='item' separator=','>" +
            "(#{item.id}, #{item.userId}, #{item.type}, #{item.category}, #{item.title}, #{item.body}, #{item.link}, #{item.metadata}, #{item.isRead}, #{item.readAt}, #{item.createdAt})" +
            "</foreach></script>")
    int batchInsert(@Param("list") List<Notification> list);
    ```
  - Verify column names against entity fields.
- **MIRROR**: New pattern consistent with existing annotation-only mapper style
- **IMPORTS**: `java.util.List`, `org.apache.ibatis.annotations.Insert`, `org.apache.ibatis.annotations.Param`
- **GOTCHA**: Column names must match the database schema exactly; if entities use camelCase with `@TableField`, the `@Insert` SQL uses database column names (snake_case) unless aliased
- **VALIDATE**: `./mvnw compile` passes

### Task 7: Replace looped inserts with batch inserts in services
- **ACTION**: Replace individual `insert()` calls in loops with `batchInsert()` calls.
- **IMPLEMENT**:
  - `AdminContestServiceImpl.java`:
    - In `createContest` (lines 137-148): build `List<ContestProblem> contestProblems`, then `contestProblemMapper.batchInsert(contestProblems)`
    - In `updateContest` (lines 207-219): same pattern after delete
    - Fix the fully-qualified `com.ulticode.modules.contest.service.RankingService` import at line 49
  - `AdminNotificationServiceImpl.java`:
    - In `createSystemNotification` (lines 104-106): build `List<Notification> notificationsToCreate`, then `notificationMapper.batchInsert(notificationsToCreate)`
- **MIRROR**: SERVICE_PATTERN (constructor-injected mapper usage)
- **IMPORTS**: none new for service files (already have mappers)
- **GOTCHA**: `batchInsert` returns `int` (rows affected), not the generated IDs; ensure any subsequent code that needs IDs (e.g., audit log) still works. For ContestProblem, IDs are not auto-generated strings; for Notification, `id` may need to be pre-assigned if used in audit log.
- **VALIDATE**: `./mvnw test -Dtest=AdminForumServiceImplTest,AdminSubmissionServiceImplTest` passes

### Task 8: Fix notification deduplication logic
- **ACTION**: Make dedup key more specific to avoid over-deduplication.
- **IMPLEMENT**:
  - In `AdminNotificationServiceImpl.java` lines 53-54:
    ```java
    String key = notification.getId() != null ? notification.getId() :
                 (notification.getTitle() + "_" + notification.getType() + "_" + notification.getCreatedAt());
    ```
  - Or use `createdAt` with full timestamp (not just `toLocalDate()`) plus title+type.
- **MIRROR**: none — logic fix
- **IMPORTS**: none
- **GOTCHA**: If `id` is null before insert, fallback to title+type+createdAt; after insert the id is populated
- **VALIDATE**: `./mvnw compile` passes

### Task 9: Narrow broad catch blocks in bulk operations
- **ACTION**: Replace `catch (Exception e)` with `catch (RuntimeException e)` in all bulk action methods; add re-throw for `DataAccessException`.
- **IMPLEMENT**:
  - Files to update: `AdminCommentServiceImpl.java`, `AdminForumServiceImpl.java`, `AdminSolutionServiceImpl.java`, `AdminUserServiceImpl.java`
  - Change:
    ```java
    } catch (RuntimeException e) {
        // Log and record failure for this item
        log.error("Failed to perform action {} on ...", ...);
        item.setSuccess(false);
        item.setError(e.getMessage());
        // Do NOT re-throw; bulk operations must report per-item failures
    }
    ```
  - Remove the `// broad catch` comments; replace with a brief note that `RuntimeException` covers business errors while allowing fatal errors to propagate.
- **MIRROR**: none — defensive fix
- **IMPORTS**: none new
- **GOTCHA**: `BusinessException` extends `RuntimeException` (verify in project); if it extends `Exception`, keep `Exception`. Check `BusinessException` hierarchy first.
- **VALIDATE**: `./mvnw compile` passes

### Task 10: Fix silent date parse failure in AdminUserServiceImpl.banUser
- **ACTION**: Throw validation exception instead of silently continuing when `until` is unparsable.
- **IMPLEMENT**:
  - In `AdminUserServiceImpl.java` lines 117-121:
    ```java
    try {
        wrapper.set(User::getBannedUntil, LocalDateTime.parse(until));
    } catch (DateTimeParseException e) {
        throw new BusinessException(ErrorCode.VALIDATION_FAILED,
            "Invalid banned_until date format: " + until);
    }
    ```
- **MIRROR**: ERROR_HANDLING from `AdminSubmissionServiceImpl.java:348-352`
- **IMPORTS**: none new
- **GOTCHA**: Ensure `DateTimeParseException` import exists (`java.time.format.DateTimeParseException`)
- **VALIDATE**: `./mvnw compile` passes

### Task 11: Fix fully-qualified class names and misleading method names
- **ACTION**: Clean up imports and rename method.
- **IMPLEMENT**:
  - `AdminContestServiceImpl.java`: add import `com.ulticode.modules.contest.service.RankingService` and replace the FQN at line 49
  - `AdminTagServiceImpl.java`: add import `com.ulticode.modules.problem.entity.ProblemTagRelation` and replace FQNs at lines 327-330
  - `AdminCommentServiceImpl.java`: rename `getAllComments` → `getForumCommentsAsFallback` and update its call site at line 64
- **MIRROR**: existing import style in each file
- **IMPORTS**: as listed above
- **GOTCHA**: Ensure no other file references `getAllComments` (it's private, so safe)
- **VALIDATE**: `./mvnw compile` passes

### Task 12: Update tests for mapper signature changes
- **ACTION**: Adjust `AdminForumServiceImplTest` mocks if `countByPostId` was mocked.
- **IMPLEMENT**:
  - If `AdminForumServiceImplTest` mocks `forumCommentMapper.countByPostId`, replace with `countByPostIds`
  - If `AdminForumServiceImplTest` mocks `edgeOperationMapper.countByTargetAndOperation`, replace with `countByTargetsAndOperation`
  - Add mock setup for the new batch methods returning a `List<Map<String, Object>>` or adjust test data
  - Run all admin service tests
- **MIRROR**: TEST_STRUCTURE from `AdminForumServiceImplTest.java`
- **IMPORTS**: `java.util.Map`, `java.util.List`
- **GOTCHA**: `countByPostIds` returns `List<Map<String, Object>>`, not a scalar; mock accordingly
- **VALIDATE**: `./mvnw test -Dtest=AdminForumServiceImplTest,AdminSubmissionServiceImplTest` passes

---

## Testing Strategy

### Unit Tests

| Test | Input | Expected Output | Edge Case? |
|---|---|---|---|
| `getPosts` with 2 posts | Post IDs `p1`, `p2` | `countByPostIds` called once with both IDs; VOs have correct counts | N/A |
| `getForumComments` with 3 comments | Author IDs `a1`, `a2` | `selectBatchIds` called once for users and once for posts | N/A |
| `getSolutions` with 2 solutions | Problem IDs `1L`, `2L` | `selectBatchIds` called once for problems | N/A |
| `updateListProblems` with null problems | `dto.setProblems(null)` | Throws `BusinessException(VALIDATION_FAILED)` | Yes |
| `banUser` with bad date | `until="not-a-date"` | Throws `BusinessException(VALIDATION_FAILED)` | Yes |
| `batchRejudge` with DB failure | `queueService` throws | `RuntimeException` caught per-item, not propagated | Yes |

### Edge Cases Checklist
- [ ] Empty result set from mapper — batch loaders must handle empty `records` gracefully (skip `selectBatchIds` if set is empty)
- [ ] `countByPostIds` / `countByTargetsAndOperation` returning empty list — maps should default to empty, VOs should show 0
- [ ] `batchInsert` with empty list — mapper method should handle empty list safely (`<foreach>` on empty list produces invalid SQL; guard with `if (!list.isEmpty())` before calling)
- [ ] Concurrent bulk operations — not changed, no new concurrency concerns

---

## Validation Commands

### Static Analysis
```bash
cd /home/davidhlp/project/UltiCode-Public-Next/backend-spring && ./mvnw compile
```
EXPECT: Zero compilation errors

### Unit Tests
```bash
cd /home/davidhlp/project/UltiCode-Public-Next/backend-spring && ./mvnw test -Dtest=AdminForumServiceImplTest,AdminSubmissionServiceImplTest
```
EXPECT: All tests pass

### Full Test Suite
```bash
cd /home/davidhlp/project/UltiCode-Public-Next/backend-spring && ./mvnw test
```
EXPECT: No regressions in admin module tests

### Integration Tests
```bash
cd /home/davidhlp/project/UltiCode-Public-Next/backend-spring && ./mvnw verify -Pci
```
EXPECT: All integration tests pass (if CI profile runs them)

### Manual Validation
- [ ] Start backend and query `/api/admin/forum/posts` — verify posts load with correct comment/vote counts
- [ ] Query `/api/admin/comments?type=forum` — verify comments load with correct usernames and parent titles
- [ ] Query `/api/admin/solutions` — verify solutions load with correct author and problem info
- [ ] Attempt to update problem list with empty/null `problems` body — verify 400 response
- [ ] Ban user with invalid `until` date — verify 400 response

---

## Acceptance Criteria
- [ ] All 4 HIGH issues resolved (N+1 queries eliminated, null check added)
- [ ] All 4 MEDIUM issues addressed (batch inserts, dedup fix, catch narrowing, parse validation)
- [ ] All 3 LOW issues fixed (imports, method rename)
- [ ] `./mvnw compile` passes
- [ ] `./mvnw test` passes with no regressions
- [ ] Tests updated for new mapper signatures
- [ ] No new XML mapper files introduced
- [ ] No `IService` pattern introduced

## Completion Checklist
- [ ] Code follows discovered patterns (batch loading from `AdminSubmissionServiceImpl`)
- [ ] Error handling matches codebase style (`BusinessException` with `ErrorCode`)
- [ ] Logging follows codebase conventions (`log.info` / `log.error` with placeholders)
- [ ] Tests follow test patterns (Mockito + AssertJ)
- [ ] No hardcoded values
- [ ] No unnecessary scope additions
- [ ] Self-contained — no questions needed during implementation

## Risks
| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| MyBatis-Plus annotation `<script>` + `<foreach>` does not work as expected | Low | High | Test `batchInsert` with empty and non-empty lists immediately; guard empty lists in service |
| `selectBatchIds` throws on empty collection | Low | Medium | Already guarded with `if (!set.isEmpty())` in pattern |
| Changing `toAdminVO` signatures breaks tests or other callers | Medium | Medium | Keep old overloads for single-item/detail views; only add new overloads for list views |
| Batch insert column names mismatch database schema | Low | High | Verify entity `@TableName` / `@TableField` annotations against SQL columns |

## Notes
- The project uses **annotation-only MyBatis-Plus mappers** (no XML files found under `src/main/resources`). All new mapper methods must use `@Select` / `@Insert` annotations with `<script>` wrappers for dynamic SQL.
- `BaseMapper.selectBatchIds` is inherited by all mappers in this project and is the standard pattern for batch-loading (used in `AdminSubmissionServiceImpl`, `AuditServiceImpl`, `FollowServiceImpl`, etc.).
- `BusinessException` is a checked or unchecked exception? Verify before narrowing catch blocks. If it extends `Exception`, keep `catch (Exception e)` but add `if (e instanceof Error) throw e;` re-throw for fatal errors.
- The `AuditHelper` audit logging calls should NOT be changed by this plan; they are out of scope.
