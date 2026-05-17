# Local Code Review: Audit Logging Integration

**Reviewed**: 2026-05-17
**Branch**: feat/integrate-audit-logging
**Decision**: REQUEST CHANGES

## Summary

This PR integrates `AuditHelper` audit logging across all admin service implementations. The audit log calls are generally well-placed and capture meaningful old/new state. However, several **HIGH** severity issues need to be addressed before merge, primarily around N+1 query problems introduced or exacerbated by the refactoring, and a null-safety bug.

## Findings

### CRITICAL
None.

### HIGH

#### 1. N+1 Query in AdminForumServiceImpl.toAdminVO
**File**: `backend-spring/src/main/java/com/ulticode/modules/admin/service/impl/AdminForumServiceImpl.java:334-338`

For every post in the result list, the code performs 3 additional queries:
- `forumCommentMapper.countByPostId(post.getId())`
- `edgeOperationMapper.countByTargetAndOperation(...VOTE_UP)`
- `edgeOperationMapper.countByTargetAndOperation(...VOTE_DOWN)`

If the page size is 100, this results in 300 extra queries per page.

**Suggested fix**: Pre-compute comment counts and vote counts in batch SQL queries (e.g., `IN` clause grouped by `postId`), or use a single join query in the mapper.

---

#### 2. N+1 Query in AdminCommentServiceImpl
**File**: `backend-spring/src/main/java/com/ulticode/modules/admin/service/impl/AdminCommentServiceImpl.java:348-423`

Both `forumToAdminVO` and `solutionToAdminVO` fetch user and parent entity (post/solution) per comment:
- `userMapper.selectById(comment.getAuthorId())`
- `forumPostMapper.selectById(comment.getPostId())` / `solutionMapper.selectById(comment.getSolutionId())`

For a page of 100 comments, this is 200 extra queries.

**Suggested fix**: Batch-load users and posts/solutions using `selectBatchIds`, similar to the pattern already used in `AdminSubmissionServiceImpl.getSubmissions()` (WR-05).

---

#### 3. N+1 Query in AdminSolutionServiceImpl.toAdminVO
**File**: `backend-spring/src/main/java/com/ulticode/modules/admin/service/impl/AdminSolutionServiceImpl.java:277-327`

The `toAdminVO` method fetches user and problem individually per solution:
- `userMapper.selectById(solution.getUserId())`
- `problemMapper.selectById(solution.getProblemId())`

**Suggested fix**: Use the same batch-loading pattern as `AdminSubmissionServiceImpl`, or create a list-view `toAdminVO` that accepts pre-loaded maps.

---

#### 4. Missing null check in AdminProblemListServiceImpl.updateListProblems
**File**: `backend-spring/src/main/java/com/ulticode/modules/admin/service/impl/AdminProblemListServiceImpl.java:183`

```java
for (UpdateProblemListProblemsDTO.ProblemEntry entry : dto.getProblems()) {
```

If `dto.getProblems()` returns null, this throws NPE. The DTO should be validated at the boundary, but the service should also defensively check.

**Suggested fix**: Add `if (dto.getProblems() == null) { throw new BusinessException(ErrorCode.BAD_REQUEST, ...); }` before the loop.

---

### MEDIUM

#### 5. Inefficient individual inserts in loops
**Files**:
- `AdminContestServiceImpl.java:137-148` (createContest)
- `AdminContestServiceImpl.java:207-219` (updateContest)
- `AdminNotificationServiceImpl.java:104-106` (createSystemNotification)

All use loops with individual `insert()` calls instead of batch inserts. For bulk operations like notifications to thousands of users, this is a significant performance bottleneck.

**Suggested fix**: Use MyBatis-Plus `saveBatch()` or `insertBatchSomeColumn` for batch inserts.

---

#### 6. Notification deduplication logic may over-deduplicate
**File**: `AdminNotificationServiceImpl.java:53-54`

```java
String key = notification.getTitle() + "_" + notification.getType() + "_" +
             notification.getCreatedAt().toLocalDate();
```

If two distinct system notifications happen to share the same title and type on the same day, they will be incorrectly deduplicated. The grouping logic assumes title+type+date is globally unique, which may not hold.

**Suggested fix**: Include a unique identifier (e.g., a system announcement ID or the first notification's ID) in the grouping key, or group by `createdAt` with finer granularity.

---

#### 7. Broad catch blocks swallow unexpected errors
**Files**: Multiple (`AdminCommentServiceImpl`, `AdminForumServiceImpl`, `AdminSolutionServiceImpl`, `AdminUserServiceImpl`)

Several bulk action methods use `catch (Exception e)` which catches everything including `OutOfMemoryError`, `NullPointerException`, etc. While these are annotated with `// broad catch: bulk operation must report per-item failures`, catching `Exception` without distinction means programming errors are silently logged and returned as generic failures.

**Suggested fix**: Catch `RuntimeException` or more specific exceptions, and consider re-throwing errors that indicate system failure (e.g., `DataAccessException`) rather than treating them as item-level failures.

---

#### 8. Date parse failure silently ignored in banUser
**File**: `AdminUserServiceImpl.java:117-121`

```java
try {
    wrapper.set(User::getBannedUntil, LocalDateTime.parse(until));
} catch (DateTimeParseException e) {
    log.warn("Failed to parse banned_until date: {}", until);
}
```

If the admin provides an invalid date format, the user is still banned but with no expiration. This could lead to unexpected permanent bans.

**Suggested fix**: Throw `BusinessException(ErrorCode.VALIDATION_FAILED, ...)` instead of silently continuing.

---

### LOW

#### 9. Fully qualified class names used instead of imports
**Files**:
- `AdminContestServiceImpl.java:49` — `com.ulticode.modules.contest.service.RankingService`
- `AdminTagServiceImpl.java:327-330` — `com.ulticode.modules.problem.entity.ProblemTagRelation`

These should be imported normally for consistency.

---

#### 10. getAllComments fallback behavior is misleading
**File**: `AdminCommentServiceImpl.java:146-150`

```java
private PageResult<AdminCommentVO> getAllComments(AdminCommentQueryDTO query, int page, int limit) {
    return getForumComments(query, page, limit);
}
```

The method name implies it returns both forum and solution comments, but it only returns forum comments. This could confuse callers.

**Suggested fix**: Rename to `getForumCommentsAsFallback` or implement proper merged pagination.

---

#### 11. Unused import in AdminForumServiceImpl
**File**: `AdminForumServiceImpl.java`

`EdgeOperationTargetType` and `EdgeOperationType` are imported but used only for their `.name()` values in `toAdminVO`. This is fine but the enum values could be passed directly if the mapper accepts enums.

---

## Validation Results

| Check | Result |
|---|---|
| Compilation | Skipped (review only) |
| Tests | Present — `AdminForumServiceImplTest` and `AdminSubmissionServiceImplTest` cover audit logging |
| Security scan | No hardcoded secrets, SQL injection, or XSS found |

## Files Reviewed

| File | Type | Key Changes |
|---|---|---|
| `AdminCommentServiceImpl.java` | Modified | Added audit logging; N+1 issue in VO mapping |
| `AdminContestServiceImpl.java` | Modified | Added audit logging; individual inserts in loops |
| `AdminForumServiceImpl.java` | Modified | Added audit logging; severe N+1 in VO mapping |
| `AdminNotificationServiceImpl.java` | Modified | Added audit logging; dedup logic concern |
| `AdminProblemListServiceImpl.java` | Modified | Added audit logging; missing null check |
| `AdminSolutionServiceImpl.java` | Modified | Added audit logging; N+1 in VO mapping |
| `AdminSubmissionServiceImpl.java` | Modified | Added audit logging; good batch-loading pattern |
| `AdminTagServiceImpl.java` | Modified | Added audit logging; mergeTag incomplete for forum |
| `AdminUserServiceImpl.java` | Modified | Added audit logging; date parse silently ignored |
| `AdminForumServiceImplTest.java` | Modified | Tests for audit logging in pin/unpin/lock/unlock/delete |
| `AdminSubmissionServiceImplTest.java` | Modified | Tests for rejudge audit logging |
| `CLAUDE.md` | Modified | PM2 port documentation update |

## Next Steps

1. **Fix HIGH issues** (N+1 queries and null check) before merging.
2. **Address MEDIUM issues** (batch inserts, dedup logic, broad catch blocks) in follow-up or as part of this PR.
3. Run `./mvnw test` to ensure all tests pass after fixes.
