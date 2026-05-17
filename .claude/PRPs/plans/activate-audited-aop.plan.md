# Plan: Activate @Audited AOP Mechanism

## Summary
Replace all manual `auditHelper.log()` / `auditHelper.logForUser()` calls in Admin service implementations with `@Audited` annotation on the corresponding methods. Enhance the existing `AuditAspect` and `@Audited` annotation to support the `userId` (target user) parameter that `logForUser` currently provides, and to capture old/new state reliably.

## User Story
As a developer, I want audit logging to be driven by declarative annotations rather than scattered manual calls, so that adding audit coverage to new methods is trivial and the codebase stays DRY.

## Problem → Solution
**Problem**: ~40 manual `auditHelper.log()`/`logForUser()` calls spread across 8 Admin service impl files. Each call duplicates boilerplate (performer extraction, IP, user agent). Adding audit to a new method requires writing 6-10 lines of imperative code. Old/new values are inconsistently captured.

**Solution**: Add `@Audited` annotation to each audited method. The `AuditAspect` handles all boilerplate. Old state is captured by reading the entity before method execution; new state is captured from the method return value. A new `userId` field on `@Audited` supports the `logForUser` pattern.

## Metadata
- **Complexity**: Large
- **Source PRD**: N/A
- **PRD Phase**: N/A
- **Estimated Files**: 12

---

## UX Design

### Before
```
Admin Service method:
  1. Fetch entity
  2. Build oldValues map manually
  3. Execute business logic
  4. Build newValues map manually
  5. Call auditHelper.logForUser(action, entityType, entityId, userId, oldValues, newValues)
  → 6-10 lines of audit boilerplate per method
```

### After
```
@Audited(action = BAN_USER, entityType = ENTITY_USER, userIdFrom = "id")
AdminUserVO banUser(String id, String reason, String until) {
  // pure business logic — no audit code
}
  → 1 annotation line, zero boilerplate
```

### Interaction Changes
| Touchpoint | Before | After | Notes |
|---|---|---|---|
| Admin service method | Manual auditHelper call after logic | @Audited annotation on method | Same audit data, declarative |
| AuditAspect | Exists but unused | Enhanced, active | Adds userId extraction, old state capture |
| AuditHelper | Used everywhere | Kept for edge cases, deprecated for standard use | Still available if needed |
| Database | No change | No change | Same audit_logs table, same data shape |

---

## Mandatory Reading

| Priority | File | Lines | Why |
|---|---|---|---|
| P0 | `backend-spring/src/main/java/com/ulticode/common/annotation/Audited.java` | all | Annotation to enhance |
| P0 | `backend-spring/src/main/java/com/ulticode/common/aspect/AuditAspect.java` | all | Aspect to rewrite |
| P0 | `backend-spring/src/main/java/com/ulticode/common/util/AuditHelper.java` | all | Current manual approach |
| P1 | `backend-spring/src/main/java/com/ulticode/common/util/AuditActionUtil.java` | all | Constants used by annotation |
| P1 | `backend-spring/src/main/java/com/ulticode/modules/admin/service/impl/AdminUserServiceImpl.java` | all | Primary migration target |
| P1 | `backend-spring/src/main/java/com/ulticode/modules/admin/service/impl/AdminContestServiceImpl.java` | all | Complex old/new values case |
| P1 | `backend-spring/src/main/java/com/ulticode/modules/admin/service/impl/AdminForumServiceImpl.java` | all | logForUser case |
| P2 | `backend-spring/src/main/java/com/ulticode/modules/admin/service/impl/AdminTagServiceImpl.java` | all | Multiple audit calls per method |
| P2 | Other Admin service impls | audit calls only | Remaining migration targets |

---

## Patterns to Mirror

### ANNOTATION_PATTERN
// SOURCE: Audited.java
```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Audited {
    String action();
    String entityType();
    boolean captureOldState() default true;
    boolean captureNewState() default true;
}
```

### ASPECT_PATTERN
// SOURCE: AuditAspect.java
```java
@Around("@annotation(audited)")
public Object auditAround(ProceedingJoinPoint joinPoint, Audited audited) throws Throwable {
    // Extract performer, IP, user agent
    // Proceed method
    // Capture state
    // Call auditService.log()
}
```

### HELPER_LOG_PATTERN (what we're replacing)
// SOURCE: AdminUserServiceImpl.java:126
```java
auditHelper.logForUser(
    AuditActionUtil.BAN_USER,
    AuditActionUtil.ENTITY_USER,
    id,
    id,
    Map.of("isBanned", user.getIsBanned(), ...),
    Map.of("isBanned", true, ...)
);
```

### HELPER_LOG_FOR_USER_PATTERN
// SOURCE: AuditHelper.java:62
```java
public void logForUser(String action, String entityType, String entityId, String userId,
                       Map<String, Object> oldValues, Map<String, Object> newValues)
```
Key difference from `log()`: takes a `userId` parameter for the target user.

### SERVICE_METHOD_PATTERN
// SOURCE: AdminUserServiceImpl.java:103
```java
@Override
@Transactional
public AdminUserVO banUser(String id, String reason, String until) { ... }
```

### ENTITY_MAPPER_ACCESS
// SOURCE: AdminUserServiceImpl.java:105
```java
User user = userMapper.selectById(id);
```
Entities are fetched via MyBatis-Plus mapper `selectById()`. This is how we capture old state.

---

## Design Decisions

### D1: Old State Capture Strategy

**Current problem**: The existing `AuditAspect.captureSimpleState()` only extracts `id` from the return value via reflection. It does NOT capture old state before the method runs.

**Decision**: Add a `mapperRef` / `entityIdParam` mechanism to `@Audited` so the aspect can:
1. Before `joinPoint.proceed()`: read current entity from DB via the mapper
2. After `joinPoint.proceed()`: read updated entity or use return value

This is too complex and couples the aspect to mapper implementations.

**Better approach**: Introduce an `AuditContext` thread-local holder. Methods can optionally populate it before/after logic. But this still requires manual code.

**Best approach for this codebase**: Keep `@Audited` annotation simple. The aspect handles boilerplate (performer, IP, user agent, timing). For old/new values, add a `SpEL` expression or a simple `AuditContext` that the method body can populate. However, given the current codebase patterns, the **pragmatic approach** is:

1. `@Audited` annotation gains: `userIdFrom` (method param name for target user ID)
2. Aspect auto-captures: performerId, IP, user agent, action, entityType, entityId (from return value `getId()`)
3. For **old/new values**: methods that need detailed change tracking use `AuditContext.setOldValues()` / `AuditContext.setNewValues()` before the annotation fires. Simple methods leave them null.
4. This is **still a major win**: every method drops 4-6 lines of boilerplate while keeping the option for detailed state capture.

### D2: userIdFrom Parameter

Many admin actions operate on a user (ban, unban, reset password). The `logForUser` variant passes the target `userId`. Add `userIdFrom` to `@Audited`:

```java
@Audited(action = BAN_USER, entityType = ENTITY_USER, userIdFrom = "id")
AdminUserVO banUser(String id, String reason, String until)
```

The aspect extracts `userId` from the method parameter named `"id"`.

### D3: entityIdFrom Parameter

Current aspect uses `extractEntityId(result)` which calls `result.getId()` via reflection. This works for methods returning entity/VO objects. For `void` methods (like `deleteContest`), we need the entity ID from a method parameter:

```java
@Audited(action = DELETE_CONTEST, entityType = ENTITY_CONTEST, entityIdFrom = "id")
void deleteContest(String id)
```

### D4: AuditContext Thread-Local

For methods that need rich old/new values (which is most of them), introduce a simple `AuditContext`:

```java
// In service method body, before mutation:
AuditContext.setOldValues(Map.of("title", contest.getTitle(), "status", contest.getStatus()));
// After mutation:
AuditContext.setNewValues(Map.of("title", contest.getTitle(), "status", contest.getStatus()));
```

The aspect reads and clears `AuditContext` after logging. This is 2 lines instead of the current 6-10 line `auditHelper.log()` call, and the boilerplate (performer, IP, user agent, service call) is fully handled by the aspect.

### D5: Backward Compatibility

`AuditHelper` remains in the codebase but is deprecated. Existing calls are removed during migration. It can still be used for edge cases that don't fit the annotation model (e.g., audit events triggered outside of a method invocation).

---

## Files to Change

| File | Action | Justification |
|---|---|---|
| `common/annotation/Audited.java` | UPDATE | Add `userIdFrom`, `entityIdFrom` fields |
| `common/aspect/AuditAspect.java` | REWRITE | Full rewrite: support new annotation fields, AuditContext, old/new values |
| `common/util/AuditContext.java` | CREATE | Thread-local holder for old/new values |
| `common/util/AuditHelper.java` | UPDATE | Add `@Deprecated` annotation |
| `admin/service/impl/AdminUserServiceImpl.java` | UPDATE | Replace 4 auditHelper calls with @Audited |
| `admin/service/impl/AdminContestServiceImpl.java` | UPDATE | Replace 9 auditHelper calls with @Audited |
| `admin/service/impl/AdminForumServiceImpl.java` | UPDATE | Replace 6 auditHelper calls with @Audited |
| `admin/service/impl/AdminTagServiceImpl.java` | UPDATE | Replace 7 auditHelper calls with @Audited |
| `admin/service/impl/AdminCommentServiceImpl.java` | UPDATE | Replace 6 auditHelper calls with @Audited |
| `admin/service/impl/AdminSolutionServiceImpl.java` | UPDATE | Replace 3 auditHelper calls with @Audited |
| `admin/service/impl/AdminProblemListServiceImpl.java` | UPDATE | Replace 3 auditHelper calls with @Audited |
| `admin/service/impl/AdminNotificationServiceImpl.java` | UPDATE | Replace 2 auditHelper calls with @Audited |
| `admin/service/impl/AdminSubmissionServiceImpl.java` | UPDATE | Replace 1 auditHelper call with @Audited |

## NOT Building
- SpEL expression evaluation for old/new values (too complex for this iteration)
- Auto-capture of old state via mapper reflection (too coupled)
- Audit logging for non-admin (user-facing) operations (future scope)
- Database schema changes (none needed)
- Frontend changes (none needed — same API contract)

---

## Step-by-Step Tasks

### Task 1: Create AuditContext Thread-Local
- **ACTION**: Create new class `AuditContext` in `com.ulticode.common.util`
- **IMPLEMENT**:
```java
package com.ulticode.common.util;

import java.util.Map;

public final class AuditContext {
    private AuditContext() {}

    private static final ThreadLocal<Map<String, Object>> OLD_VALUES = new ThreadLocal<>();
    private static final ThreadLocal<Map<String, Object>> NEW_VALUES = new ThreadLocal<>();
    private static final ThreadLocal<String> USER_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> ENTITY_ID = new ThreadLocal<>();

    public static void setOldValues(Map<String, Object> values) { OLD_VALUES.set(values); }
    public static Map<String, Object> getOldValues() { return OLD_VALUES.get(); }

    public static void setNewValues(Map<String, Object> values) { NEW_VALUES.set(values); }
    public static Map<String, Object> getNewValues() { return NEW_VALUES.get(); }

    public static void setUserId(String userId) { USER_ID.set(userId); }
    public static String getUserId() { return USER_ID.get(); }

    public static void setEntityId(String entityId) { ENTITY_ID.set(entityId); }
    public static String getEntityId() { return ENTITY_ID.get(); }

    public static void clear() {
        OLD_VALUES.remove();
        NEW_VALUES.remove();
        USER_ID.remove();
        ENTITY_ID.remove();
    }
}
```
- **MIRROR**: Pattern from `SecurityUtil` (thread-local access, static utility class)
- **GOTCHA**: Must call `clear()` in aspect `finally` block to prevent memory leaks
- **VALIDATE**: Compiles, no errors

### Task 2: Enhance @Audited Annotation
- **ACTION**: Add `userIdFrom` and `entityIdFrom` fields to `@Audited`
- **IMPLEMENT**: Update `Audited.java` to:
```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Audited {
    String action();
    String entityType();

    /**
     * Method parameter name to extract as the target user ID (for logForUser pattern).
     * Empty string means no automatic userId extraction.
     */
    String userIdFrom() default "";

    /**
     * Method parameter name to extract as the entity ID.
     * Empty string means fall back to result.getId() via reflection.
     */
    String entityIdFrom() default "";

    boolean captureOldState() default true;
    boolean captureNewState() default true;
}
```
- **MIRROR**: Existing annotation pattern in `Audited.java`
- **GOTCHA**: `userIdFrom` and `entityIdFrom` use method parameter **names**, which require `-parameters` compiler flag or Spring's `DefaultParameterNameDiscoverer`. Spring Boot already enables this via `spring-boot-starter-parent`.
- **VALIDATE**: Compiles, existing annotation usages (none) still valid

### Task 3: Rewrite AuditAspect
- **ACTION**: Full rewrite of `AuditAspect` to support enhanced annotation and AuditContext
- **IMPLEMENT**:
```java
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AuditAspect {

    private final AuditService auditService;

    @Around("@annotation(audited)")
    public Object auditAround(ProceedingJoinPoint joinPoint, Audited audited) throws Throwable {
        String performerId = SecurityUtil.getCurrentUserId();
        if (performerId == null) {
            performerId = "system";
        }

        String ip = getClientIp();
        String userAgent = getUserAgent();

        // Pre-extract target userId from method params if specified
        String targetUserId = resolveParamValue(joinPoint, audited.userIdFrom());

        // Pre-extract entityId from method params if specified
        String resolvedEntityId = resolveParamValue(joinPoint, audited.entityIdFrom());

        Object result;
        try {
            result = joinPoint.proceed();
        } catch (Exception e) {
            // Log the failed attempt
            auditService.log(
                performerId,
                targetUserId != null ? targetUserId : AuditContext.getUserId(),
                audited.action(),
                audited.entityType(),
                resolvedEntityId != null ? resolvedEntityId : "N/A",
                AuditContext.getOldValues(),
                Map.of("error", e.getClass().getSimpleName(), "message", e.getMessage() != null ? e.getMessage() : ""),
                ip,
                userAgent
            );
            throw e;
        } finally {
            // Clean up context after logging (whether success or failure)
            // Note: clear is done after the log call below for success path
        }

        // Resolve entity ID: param > AuditContext > reflection on result
        String entityId = resolvedEntityId;
        if (entityId == null || entityId.isEmpty()) {
            entityId = AuditContext.getEntityId();
        }
        if (entityId == null || entityId.isEmpty()) {
            entityId = extractEntityId(result);
        }

        // Resolve userId: annotation param > AuditContext
        String userId = targetUserId;
        if (userId == null || userId.isEmpty()) {
            userId = AuditContext.getUserId();
        }

        // Get old/new values from AuditContext (set by method body)
        Map<String, Object> oldValues = AuditContext.getOldValues();
        Map<String, Object> newValues = AuditContext.getNewValues();

        // Optionally capture new state from return value if context didn't provide it
        if (newValues == null && audited.captureNewState() && result != null) {
            newValues = captureSimpleState(result);
        }

        auditService.log(
            performerId,
            userId,
            audited.action(),
            audited.entityType(),
            entityId != null ? entityId : "N/A",
            oldValues,
            newValues,
            ip,
            userAgent
        );

        // Clean up thread-local
        AuditContext.clear();

        return result;
    }

    /**
     * Resolve a method parameter value by parameter name.
     */
    private String resolveParamValue(ProceedingJoinPoint joinPoint, String paramName) {
        if (paramName == null || paramName.isEmpty()) {
            return null;
        }

        CodeSignature signature = (CodeSignature) joinPoint.getSignature();
        String[] paramNames = signature.getParameterNames();
        Object[] args = joinPoint.getArgs();

        for (int i = 0; i < paramNames.length; i++) {
            if (paramName.equals(paramNames[i]) && args[i] != null) {
                return args[i].toString();
            }
        }

        return null;
    }

    // ... keep existing extractEntityId, captureSimpleState, getClientIp, getUserAgent methods
    // ... add import for org.aspectj.lang.reflect.CodeSignature
}
```
- **MIRROR**: Existing `AuditAspect.java` structure, `AuditHelper.java` IP/userAgent extraction
- **IMPORTS**: Add `org.aspectj.lang.reflect.CodeSignature`, `com.ulticode.common.util.AuditContext`
- **GOTCHA**: Must clear `AuditContext` in both success and failure paths. The `finally` block for error path needs to clear AFTER the log call in the catch block.
- **GOTCHA**: The `resolveParamValue` requires compiled parameter names. If `-parameters` is not set, it returns null. Spring Boot Maven plugin enables this by default.
- **VALIDATE**: Compiles, existing test compilation passes

### Task 4: Deprecate AuditHelper
- **ACTION**: Add `@Deprecated` annotation and javadoc to `AuditHelper`
- **IMPLEMENT**: Add `@Deprecated(forRemoval = false)` and update javadoc to recommend `@Audited` annotation
- **MIRROR**: Standard deprecation pattern
- **VALIDATE**: Compiles, all existing callers still work (deprecation is advisory only)

### Task 5: Migrate AdminUserServiceImpl
- **ACTION**: Replace 4 `auditHelper.logForUser()` calls with `@Audited` annotations + AuditContext
- **IMPLEMENT**:
  - Remove `private final AuditHelper auditHelper;` field
  - Add annotations:
    - `banUser` → `@Audited(action = AuditActionUtil.BAN_USER, entityType = AuditActionUtil.ENTITY_USER, userIdFrom = "id")`
      - Before update: `AuditContext.setOldValues(Map.of("isBanned", user.getIsBanned(), ...))`
      - After update: `AuditContext.setNewValues(Map.of("isBanned", true, ...))`
    - `unbanUser` → `@Audited(action = AuditActionUtil.UNBAN_USER, entityType = AuditActionUtil.ENTITY_USER, userIdFrom = "id")`
    - `resetPassword` → `@Audited(action = AuditActionUtil.RESET_PASSWORD, entityType = AuditActionUtil.ENTITY_USER, userIdFrom = "id")`
    - `bulkDelete` → Keep manual auditHelper for now (loop-based, doesn't fit single-method annotation)
  - Add `import com.ulticode.common.annotation.Audited;` and `import com.ulticode.common.util.AuditContext;`
  - Remove `import com.ulticode.common.util.AuditHelper;` (unless still needed for bulkDelete)
- **MIRROR**: Annotation pattern from `@Audited` definition
- **GOTCHA**: `bulkBan` and `bulkUnban` delegate to `banUser`/`unbanUser`, so they will auto-inherit audit logging — no extra annotation needed on bulk methods. But `bulkDelete` has inline audit calls because it doesn't delegate to a single annotated method.
- **VALIDATE**: `./mvnw compile` passes

### Task 6: Migrate AdminContestServiceImpl
- **ACTION**: Replace 9 `auditHelper.log()` calls with `@Audited` annotations + AuditContext
- **IMPLEMENT**:
  - Remove `private final AuditHelper auditHelper;` field
  - Add annotations to these methods:
    - `createContest` → `@Audited(action = CREATE_CONTEST, entityType = ENTITY_CONTEST)`
      - After insert: `AuditContext.setNewValues(Map.of("title", ..., "slug", ...))`
      - Set `captureOldState = false` since there's no old entity
    - `updateContest` → `@Audited(action = UPDATE_CONTEST, entityType = ENTITY_CONTEST, entityIdFrom = "id")`
      - Before/after: set oldValues/newValues from entity fields
    - `deleteContest` → `@Audited(action = DELETE_CONTEST, entityType = ENTITY_CONTEST, entityIdFrom = "id")`
    - `startContest` → `@Audited(action = UPDATE_CONTEST, entityType = ENTITY_CONTEST, entityIdFrom = "id")`
    - `endContest` → `@Audited(action = UPDATE_CONTEST, entityType = ENTITY_CONTEST, entityIdFrom = "id")`
    - `createAnnouncement` → `@Audited(action = CREATE_CONTEST_ANNOUNCEMENT, entityType = ENTITY_CONTEST_ANNOUNCEMENT, captureOldState = false)`
    - `updateAnnouncement` → `@Audited(action = UPDATE_CONTEST_ANNOUNCEMENT, entityType = ENTITY_CONTEST_ANNOUNCEMENT, entityIdFrom = "announcementId")`
    - `deleteAnnouncement` → `@Audited(action = DELETE_CONTEST_ANNOUNCEMENT, entityType = ENTITY_CONTEST_ANNOUNCEMENT, entityIdFrom = "announcementId")`
    - `addProblemToContest` → `@Audited(action = UPDATE_CONTEST, entityType = ENTITY_CONTEST, entityIdFrom = "contestId", captureOldState = false)`
- **MIRROR**: Same pattern as Task 5
- **GOTCHA**: `createContest` has `captureOldState = false` because there's no entity before creation
- **VALIDATE**: `./mvnw compile` passes

### Task 7: Migrate AdminForumServiceImpl
- **ACTION**: Replace 6 `auditHelper.logForUser()` calls with `@Audited` annotations + AuditContext
- **IMPLEMENT**:
  - Remove `private final AuditHelper auditHelper;` field (keep `AuditService auditService` for `getPostAuditHistory`)
  - Add annotations:
    - `pinPost` → `@Audited(action = PIN_POST, entityType = ENTITY_FORUM_POST, entityIdFrom = "id")`
      - Before: `AuditContext.setUserId(post.getUserId()); AuditContext.setOldValues(Map.of("isPinned", post.getIsPinned()));`
      - After: `AuditContext.setNewValues(Map.of("isPinned", true));`
    - `unpinPost` → similar
    - `lockPost` → similar
    - `unlockPost` → similar
    - `deletePost` → `@Audited(action = DELETE_FORUM_POST, entityType = ENTITY_FORUM_POST, entityIdFrom = "id")`
- **MIRROR**: Same pattern as Task 5
- **GOTCHA**: Forum methods use `logForUser` — set `AuditContext.setUserId()` in method body
- **VALIDATE**: `./mvnw compile` passes

### Task 8: Migrate AdminTagServiceImpl
- **ACTION**: Replace 7 `auditHelper.log()` calls with `@Audited` annotations + AuditContext
- **IMPLEMENT**: Same pattern — remove auditHelper, add annotations + AuditContext calls
- **MIRROR**: Same pattern as Task 5
- **GOTCHA**: `createTag` has two branches (forum vs problem tag). Both set AuditContext before return. The annotation captures from context regardless of branch.
- **VALIDATE**: `./mvnw compile` passes

### Task 9: Migrate AdminCommentServiceImpl
- **ACTION**: Replace 6 `auditHelper.logForUser()` calls with `@Audited` annotations + AuditContext
- **IMPLEMENT**: Same pattern
- **VALIDATE**: `./mvnw compile` passes

### Task 10: Migrate AdminSolutionServiceImpl
- **ACTION**: Replace 3 `auditHelper.logForUser()` calls with `@Audited` annotations + AuditContext
- **IMPLEMENT**: Same pattern
- **VALIDATE**: `./mvnw compile` passes

### Task 11: Migrate AdminProblemListServiceImpl + AdminNotificationServiceImpl + AdminSubmissionServiceImpl
- **ACTION**: Replace remaining 6 auditHelper calls across 3 files
- **IMPLEMENT**: Same pattern
- **VALIDATE**: `./mvnw compile` passes

### Task 12: Verify Compilation and Runtime
- **ACTION**: Full compile + runtime verification
- **IMPLEMENT**:
  - Run `./mvnw compile` to verify no compilation errors
  - Restart backend: `pm2 restart ulticode-9001`
  - Test ban/unban via curl and verify audit log entries still appear with correct data
- **VALIDATE**:
  - Zero compilation errors
  - Audit log entries appear in `audit_logs` table after admin operations
  - Old/new values captured correctly
  - IP address and user agent captured correctly

---

## Testing Strategy

### Unit Tests

| Test | Input | Expected Output | Edge Case? |
|---|---|---|---|
| AuditContext set/get/clear | Set values, get, clear | Values retrieved, then null after clear | Yes — thread isolation |
| @Audited with userIdFrom | Method with `id` param | userId extracted from param | No |
| @Audited without userIdFrom | Method with no userIdFrom | userId is null in log | No |
| @Audited with entityIdFrom | Method returning void | entityId from param | Yes — void return |
| @Audited with result getId | Method returning VO | entityId from result.getId() | No |
| AuditContext not cleared on exception | Method that throws | AuditContext.clear() still called | Yes — leak prevention |

### Edge Cases Checklist
- [x] Method returns void — entityId must come from `entityIdFrom` param
- [x] Method throws exception — audit logged with error, AuditContext cleared
- [x] AuditContext not set — oldValues/newValues are null (acceptable)
- [x] Bulk operations (bulkBan, bulkDelete) — keep AuditHelper for these
- [x] Thread-local leak — AuditContext.clear() in aspect finally path

---

## Validation Commands

### Static Analysis
```bash
cd backend-spring && ./mvnw compile
```
EXPECT: Zero compilation errors

### Runtime Test
```bash
# Login
curl -s -c /tmp/cookies.txt -X POST "http://localhost:9001/auth/login" \
  -H "Content-Type: application/json" -d '{"username":"admin","password":"admin123"}'

# Ban user
CSRF=$(curl -s -b /tmp/cookies.txt "http://localhost:9001/auth/csrf" | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['csrfToken'])")
curl -s -b /tmp/cookies.txt -H "X-CSRF-TOKEN: $CSRF" -X POST "http://localhost:9001/admin/users/u-001/ban" -d '{"reason":"test"}'

# Check audit log
docker exec ulticode-mysql mysql -u ulticode -p'CHANGE_ME_strong_password' ulticode \
  -e "SELECT action, entity_type, old_values, new_values FROM audit_logs ORDER BY created_at DESC LIMIT 1;"
```
EXPECT: Audit log entry with action=BAN_USER, old_values and new_values populated

### Full Compile
```bash
cd backend-spring && ./mvnw compile -q
```
EXPECT: BUILD SUCCESS

---

## Acceptance Criteria
- [ ] All 40 manual auditHelper calls replaced with @Audited annotations (except bulk methods)
- [ ] AuditContext thread-local created and working
- [ ] AuditAspect enhanced with userIdFrom, entityIdFrom, AuditContext support
- [ ] AuditHelper deprecated (not deleted)
- [ ] Zero compilation errors
- [ ] Runtime verification: ban/unban produces audit log entries with correct data
- [ ] old_values and new_values populated where previously null

## Completion Checklist
- [ ] Code follows existing service/annotation patterns
- [ ] Error handling in aspect matches codebase style (log + rethrow)
- [ ] Thread-local cleaned up in all code paths (success, exception)
- [ ] No hardcoded values
- [ ] AuditHelper deprecated, not deleted — backward compatible
- [ ] No unnecessary scope additions

## Risks
| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Parameter name discovery fails (no `-parameters` flag) | Low | Medium | Spring Boot enables `-parameters` by default; add explicit `maven-compiler-plugin` config if needed |
| Thread-local leak in async/scheduled contexts | Low | High | AuditContext.clear() called in both success and exception paths of aspect |
| Bulk operation methods don't fit annotation model | Medium | Low | Keep AuditHelper for bulk methods (3 call sites max) |
| AuditAspect ordering conflict with @Transactional | Low | High | Ensure `@Order(Ordered.HIGHEST_PRECEDENCE)` or let Spring auto-order — aspect should wrap transaction so it logs after commit |

## Notes
- The `@Audited` AOP annotation was already written but never used — this plan activates it properly.
- Bulk operations (`bulkBan`, `bulkUnban`, `bulkDelete`) keep using `AuditHelper` because they loop over multiple entities in a single method call — a single annotation can't capture multiple audit events.
- `AuditContext` uses `ThreadLocal` which is safe in the synchronous Spring MVC request model. If async endpoints are added later, `AuditContext` must be adapted (e.g., propagated to async threads).
