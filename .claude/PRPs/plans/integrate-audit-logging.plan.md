# Plan: Integrate Audit Logging Across UltiCode Backend

## Summary

UltiCode has a complete audit log infrastructure (entity, mapper, service, controller, database table, and management frontend) but **audit logging is never actually invoked** outside the admin module's own service layer. This plan integrates audit logging into all admin operations and critical user-facing mutations, ensuring every CREATE, UPDATE, DELETE, BAN, and permission change is recorded with before/after state (oldValues/newValues), performer identity, target entity, IP address, and user agent.

## User Story

As an UltiCode platform administrator,
I want every administrative action and critical user mutation to be recorded in the audit log with full context,
So that I can trace who changed what, when, from where, and what the before/after state was.

## Problem → Solution

**Current state**: Audit log table is empty. Admin panel shows "No logs". 53 entity classes and 15 admin controllers perform mutations without any audit trail.

**Desired state**: All state-mutating admin operations and critical user actions emit structured audit records. The management frontend's AuditLogsView populates with real data. Exports contain meaningful trails.

## Metadata

- **Complexity**: Large
- **Source PRD**: N/A
- **PRD Phase**: standalone
- **Estimated Files**: 25+ files

---

## UX Design

### Before
```
┌─────────────────────────────┐
│  Admin > Audit Logs         │
│  ─────────────────────      │
│  暂无日志                    │
│  没有找到符合条件的审计日志记录。│
│  Total: 0  Create: 0        │
│  Update: 0  Delete: 0       │
└─────────────────────────────┘
```

### After
```
┌─────────────────────────────┐
│  Admin > Audit Logs         │
│  ─────────────────────      │
│  2026-05-16 10:23:05   BAN_USER   USER   admin   alice   192.168.1.1
│  2026-05-16 10:22:12   UPDATE    PROBLEM  admin   —       192.168.1.1
│  2026-05-16 09:45:33   CREATE    CONTEST  admin   —       192.168.1.1
│  Total: 1,247  Create: 45   │
│  Update: 892   Delete: 310  │
└─────────────────────────────┘
```

### Interaction Changes

| Touchpoint | Before | After | Notes |
|---|---|---|---|
| Ban user | No trace | Audit log with oldValues `{isBanned: false}` newValues `{isBanned: true, bannedReason: "spam"}` | Admin panel shows immediately |
| Update problem | No trace | Audit log with diff of changed fields | Detail drawer shows JSON diff |
| Delete user | No trace | Audit log with full user snapshot in oldValues | Exportable for compliance |
| Reset password | No trace | Audit log recorded, password field omitted from values | Security: never log passwords |
| Bulk operations | No trace | One audit log per item + summary log | Batch jobs must loop-call audit |

---

## Mandatory Reading

| Priority | File | Lines | Why |
|---|---|---|---|
| P0 (critical) | `backend-spring/src/main/java/com/ulticode/modules/admin/service/impl/AuditServiceImpl.java` | all | Core pattern to follow for audit log creation |
| P0 (critical) | `backend-spring/src/main/java/com/ulticode/modules/admin/entity/AuditLog.java` | all | Entity structure — action, entityType, entityId, oldValues, newValues |
| P0 (critical) | `backend-spring/src/main/java/com/ulticode/modules/admin/service/AuditService.java` | all | Interface — log() signature and read methods |
| P0 (critical) | `backend-spring/src/main/java/com/ulticode/common/aspect/RateLimitAspect.java` | all | Existing AOP pattern to mirror for @Audited aspect |
| P1 (important) | `backend-spring/src/main/java/com/ulticode/modules/admin/service/impl/AdminUserServiceImpl.java` | all | Example admin service where audit must be added |
| P1 (important) | `backend-spring/src/main/java/com/ulticode/common/util/SecurityUtil.java` | all | How to get current user ID and role |
| P1 (important) | `management/src/i18n/locales/zh-CN/modules/audit.ts` | all | Frontend's expected action types and entity types |
| P2 (reference) | `backend-spring/src/main/java/com/ulticode/modules/admin/controller/AuditController.java` | all | How audit logs are queried/exported |

## External Documentation

| Topic | Source | Key Takeaway |
|---|---|---|
| Spring AOP | Spring Framework Docs | Use @Around for capturing before/after state |
| Jackson ObjectMapper | `ObjectMapper` bean already configured | Use for serializing old/new values to Map |
| MyBatis-Plus | `BaseMapper` extends | Select by ID before update to capture old state |

---

## Patterns to Mirror

### NAMING_CONVENTION
```java
// SOURCE: backend-spring/src/main/java/com/ulticode/modules/admin/entity/AuditLog.java:1-32
// Actions: VERB_ENTITY (e.g., BAN_USER, UPDATE_PROBLEM, DELETE_SOLUTION)
// Entity types: Pascal-cased table names (USER, PROBLEM, CONTEST, SOLUTION, FORUM_POST)
```

### AOP_PATTERN
```java
// SOURCE: backend-spring/src/main/java/com/ulticode/common/aspect/RateLimitAspect.java:1-101
@Aspect
@Component
@RequiredArgsConstructor
public class RateLimitAspect {
    @Around("@annotation(com.ulticode.common.annotation.RateLimit)")
    public Object enforceRateLimit(ProceedingJoinPoint joinPoint) throws Throwable {
        // capture before state
        Object result = joinPoint.proceed();
        // capture after state
        return result;
    }
}
```

### SERVICE_LAYER_AUDIT
```java
// SOURCE: backend-spring/src/main/java/com/ulticode/modules/admin/service/impl/AuditServiceImpl.java:31-50
public AuditLog log(String performerId, String userId, String action,
                     String entityType, String entityId,
                     Map<String, Object> oldValues, Map<String, Object> newValues,
                     String ipAddress, String userAgent) {
    AuditLog auditLog = new AuditLog();
    auditLog.setPerformerId(performerId);
    auditLog.setUserId(userId);
    auditLog.setAction(action);
    auditLog.setEntityType(entityType);
    auditLog.setEntityId(entityId != null ? entityId : "N/A");
    auditLog.setOldValues(oldValues);
    auditLog.setNewValues(newValues);
    auditLog.setIpAddress(ipAddress);
    auditLog.setUserAgent(userAgent);
    auditLogMapper.insert(auditLog);
    return auditLog;
}
```

### SECURITY_CONTEXT_ACCESS
```java
// SOURCE: backend-spring/src/main/java/com/ulticode/common/util/SecurityUtil.java:20-26
public static String getCurrentUserId() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null && authentication.isAuthenticated()) {
        return authentication.getName();
    }
    return null;
}
```

### REQUEST_CONTEXT_ACCESS
```java
// SOURCE: backend-spring/src/main/java/com/ulticode/common/aspect/RateLimitAspect.java:82-100
private String getClientIp() {
    ServletRequestAttributes attributes =
        (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
    HttpServletRequest request = attributes.getRequest();
    String ip = request.getHeader("X-Real-IP");
    if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
        return ip.contains(",") ? ip.split(",")[0].trim() : ip;
    }
    ip = request.getRemoteAddr();
    return ip != null ? ip : "unknown";
}
```

---

## Files to Change

### New Files (CREATE)

| File | Action | Justification |
|---|---|---|
| `backend-spring/src/main/java/com/ulticode/common/annotation/Audited.java` | CREATE | Declarative annotation for methods that should trigger audit logging |
| `backend-spring/src/main/java/com/ulticode/common/aspect/AuditAspect.java` | CREATE | AOP aspect that intercepts @Audited methods, captures old/new state, and calls AuditService |
| `backend-spring/src/main/java/com/ulticode/common/util/AuditActionUtil.java` | CREATE | Utility class with standard action constants and entity type constants |

### Modified Files (UPDATE)

| File | Action | Justification |
|---|---|---|
| `backend-spring/src/main/java/com/ulticode/modules/admin/service/impl/AdminUserServiceImpl.java` | UPDATE | Add audit logging to banUser, unbanUser, resetPassword, bulkBan, bulkUnban, bulkDelete |
| `backend-spring/src/main/java/com/ulticode/modules/admin/service/impl/AdminProblemServiceImpl.java` | UPDATE | Add audit logging to create, update, delete problem operations |
| `backend-spring/src/main/java/com/ulticode/modules/admin/service/impl/AdminContestServiceImpl.java` | UPDATE | Add audit logging to contest CRUD operations |
| `backend-spring/src/main/java/com/ulticode/modules/admin/service/impl/AdminSolutionServiceImpl.java` | UPDATE | Add audit logging to solution management |
| `backend-spring/src/main/java/com/ulticode/modules/admin/service/impl/AdminSubmissionServiceImpl.java` | UPDATE | Add audit logging to submission management (requeue, delete) |
| `backend-spring/src/main/java/com/ulticode/modules/admin/service/impl/AdminForumServiceImpl.java` | UPDATE | Add audit logging to forum post/thread management |
| `backend-spring/src/main/java/com/ulticode/modules/admin/service/impl/AdminCommentServiceImpl.java` | UPDATE | Add audit logging to comment management |
| `backend-spring/src/main/java/com/ulticode/modules/admin/service/impl/AdminTagServiceImpl.java` | UPDATE | Add audit logging to tag CRUD |
| `backend-spring/src/main/java/com/ulticode/modules/admin/service/impl/AdminProblemListServiceImpl.java` | UPDATE | Add audit logging to problem list management |
| `backend-spring/src/main/java/com/ulticode/modules/admin/service/impl/AdminNotificationServiceImpl.java` | UPDATE | Add audit logging to notification management |
| `backend-spring/src/main/java/com/ulticode/modules/admin/service/impl/AdminSettingsController.java` | UPDATE | Add audit logging to settings changes |
| `backend-spring/src/main/java/com/ulticode/modules/user/service/impl/UserServiceImpl.java` | UPDATE | Add audit logging to profile updates (user-facing but state-mutating) |
| `backend-spring/src/main/java/com/ulticode/modules/permission/service/impl/PermissionServiceImpl.java` | UPDATE | Add audit logging to permission grants/revokes |
| `management/src/i18n/locales/zh-CN/modules/audit.ts` | UPDATE | Add missing action types and entity types for new operations |
| `management/src/i18n/locales/en-US/modules/audit.ts` | UPDATE | Add English translations for new action/entity types |
| `management/src/views/audit/AuditLogsView.vue` | UPDATE | Add new filter options for additional entity types and actions |

## NOT Building

- No changes to the audit log database schema (existing schema is sufficient)
- No changes to AuditController, AuditService interface, or AuditLogMapper (read side is complete)
- No audit logging for read-only queries (list, get, search operations)
- No audit logging for submission execution/judging (too high volume, not admin actions)
- No audit logging for WebSocket events (out of scope)
- No real-time audit streaming or webhook notifications (out of scope)
- No automatic audit log pruning/retention policy (out of scope)

---

## Step-by-Step Tasks

### Task 1: Define Audit Constants Utility

- **ACTION**: Create `AuditActionUtil.java` with standard action and entity type constants
- **IMPLEMENT**:
  ```java
  public final class AuditActionUtil {
      private AuditActionUtil() {}

      // Actions
      public static final String CREATE_USER = "CREATE_USER";
      public static final String UPDATE_USER = "UPDATE_USER";
      public static final String DELETE_USER = "DELETE_USER";
      public static final String BAN_USER = "BAN_USER";
      public static final String UNBAN_USER = "UNBAN_USER";
      public static final String RESET_PASSWORD = "RESET_PASSWORD";
      public static final String CREATE_PROBLEM = "CREATE_PROBLEM";
      public static final String UPDATE_PROBLEM = "UPDATE_PROBLEM";
      public static final String DELETE_PROBLEM = "DELETE_PROBLEM";
      public static final String CREATE_CONTEST = "CREATE_CONTEST";
      public static final String UPDATE_CONTEST = "UPDATE_CONTEST";
      public static final String DELETE_CONTEST = "DELETE_CONTEST";
      public static final String CREATE_SOLUTION = "CREATE_SOLUTION";
      public static final String UPDATE_SOLUTION = "UPDATE_SOLUTION";
      public static final String DELETE_SOLUTION = "DELETE_SOLUTION";
      public static final String CREATE_FORUM_POST = "CREATE_FORUM_POST";
      public static final String UPDATE_FORUM_POST = "UPDATE_FORUM_POST";
      public static final String DELETE_FORUM_POST = "DELETE_FORUM_POST";
      public static final String CREATE_TAG = "CREATE_TAG";
      public static final String UPDATE_TAG = "UPDATE_TAG";
      public static final String DELETE_TAG = "DELETE_TAG";
      public static final String GRANT_PERMISSION = "GRANT_PERMISSION";
      public static final String REVOKE_PERMISSION = "REVOKE_PERMISSION";
      public static final String UPDATE_SETTINGS = "UPDATE_SETTINGS";
      public static final String REQUEUE_SUBMISSION = "REQUEUE_SUBMISSION";
      public static final String DELETE_SUBMISSION = "DELETE_SUBMISSION";
      public static final String MODERATE_CONTENT = "MODERATE_CONTENT";

      // Entity types
      public static final String ENTITY_USER = "USER";
      public static final String ENTITY_PROBLEM = "PROBLEM";
      public static final String ENTITY_CONTEST = "CONTEST";
      public static final String ENTITY_SOLUTION = "SOLUTION";
      public static final String ENTITY_SUBMISSION = "SUBMISSION";
      public static final String ENTITY_FORUM_POST = "FORUM_POST";
      public static final String ENTITY_FORUM_COMMENT = "FORUM_COMMENT";
      public static final String ENTITY_TAG = "TAG";
      public static final String ENTITY_PROBLEM_LIST = "PROBLEM_LIST";
      public static final String ENTITY_SETTINGS = "SETTINGS";
      public static final String ENTITY_PERMISSION = "PERMISSION";
      public static final String ENTITY_NOTIFICATION = "NOTIFICATION";
  }
  ```
- **MIRROR**: Follow the existing constant naming in `SecurityUtil.java`
- **IMPORTS**: None required
- **GOTCHA**: Keep constants in sync with frontend i18n keys in `management/src/i18n/locales/*/modules/audit.ts`
- **VALIDATE**: Check that all constants referenced in this plan are defined

### Task 2: Create @Audited Annotation

- **ACTION**: Create `@Audited` annotation for declarative audit logging
- **IMPLEMENT**:
  ```java
  @Target(ElementType.METHOD)
  @Retention(RetentionPolicy.RUNTIME)
  public @interface Audited {
      String action();
      String entityType();
      String entityIdExpression() default "";
      boolean captureOldState() default true;
      boolean captureNewState() default true;
  }
  ```
- **MIRROR**: Follow `RateLimit.java` annotation pattern
- **IMPORTS**: `java.lang.annotation.*`
- **GOTCHA**: `entityIdExpression` uses SpEL (Spring Expression Language) to extract entity ID from method args/return value. For simple cases, empty string means "derive from first arg or return value"
- **VALIDATE**: Annotation compiles and is discoverable by Spring AOP

### Task 3: Create AuditAspect (AOP)

- **ACTION**: Create `AuditAspect.java` that intercepts `@Audited` methods
- **IMPLEMENT**:
  ```java
  @Aspect
  @Component
  @RequiredArgsConstructor
  @Slf4j
  public class AuditAspect {
      private final AuditService auditService;
      private final ObjectMapper objectMapper;

      @Around("@annotation(audited)")
      public Object auditAround(ProceedingJoinPoint joinPoint, Audited audited) throws Throwable {
          // Extract entity ID from args if possible
          String entityId = resolveEntityId(joinPoint, audited);

          // Capture old state if applicable (requires entityId and entityType)
          Map<String, Object> oldValues = null;
          if (audited.captureOldState() && entityId != null && !entityId.isBlank()) {
              oldValues = captureEntityState(audited.entityType(), entityId);
          }

          // Proceed with method
          Object result = joinPoint.proceed();

          // Capture new state
          Map<String, Object> newValues = null;
          if (audited.captureNewState()) {
              newValues = captureResultState(result);
          }

          // Get performer
          String performerId = SecurityUtil.getCurrentUserId();
          if (performerId == null) {
              performerId = "system";
          }

          // Get request context
          String ip = getClientIp();
          String userAgent = getUserAgent();

          auditService.log(performerId, null, audited.action(),
              audited.entityType(), entityId, oldValues, newValues, ip, userAgent);

          return result;
      }

      // ... helper methods
  }
  ```
- **MIRROR**: Follow `RateLimitAspect.java` pattern exactly
- **IMPORTS**: `org.aspectj.lang.*`, `com.ulticode.modules.admin.service.AuditService`, etc.
- **GOTCHA**: This aspect is a FALLBACK for simple cases. Complex admin operations (bulk, conditional updates) should call `auditService.log()` directly in service code because AOP cannot reliably capture old state for arbitrary update patterns
- **VALIDATE**: Unit test the aspect with a mock service method

### Task 4: Add Manual Audit Logging to AdminUserServiceImpl

- **ACTION**: Inject `AuditService` into `AdminUserServiceImpl` and add audit calls
- **IMPLEMENT**: After each mutation, call:
  ```java
  auditService.log(
      SecurityUtil.getCurrentUserId(),
      userId,  // target user
      AuditActionUtil.BAN_USER,
      AuditActionUtil.ENTITY_USER,
      userId,
      Map.of("isBanned", false, "bannedReason", oldReason),  // oldValues
      Map.of("isBanned", true, "bannedReason", reason),      // newValues
      getClientIp(),
      getUserAgent()
  );
  ```
- **MIRROR**: Follow `AuditServiceImpl.log()` pattern
- **IMPORTS**: `com.ulticode.modules.admin.service.AuditService`, `com.ulticode.common.util.AuditActionUtil`
- **GOTCHA**: For `resetPassword`, NEVER include the password in oldValues or newValues. Log only the action, not the credential
- **GOTCHA**: For `bulkBan`, `bulkUnban`, `bulkDelete`, call `auditService.log()` INSIDE the loop for each user, plus one summary log
- **VALIDATE**: Run admin user controller tests; verify audit_logs table has rows after ban/unban

### Task 5: Add Manual Audit Logging to AdminProblemServiceImpl and Related

- **ACTION**: Add audit logging to problem create/update/delete in admin problem service
- **IMPLEMENT**: Capture old state before update by fetching existing problem; capture new state from saved entity. For delete, capture full snapshot in oldValues.
- **MIRROR**: Same pattern as Task 4
- **IMPORTS**: Same as Task 4
- **GOTCHA**: Problem has many relations (details, examples, languages, tags). For update, only capture changed fields, not full nested tree, to keep JSON size reasonable
- **VALIDATE**: Verify problem update creates audit log with meaningful diff

### Task 6: Add Audit Logging to Remaining Admin Services

- **ACTION**: Add audit logging to AdminContest, AdminSolution, AdminSubmission, AdminForum, AdminComment, AdminTag, AdminProblemList, AdminNotification services
- **IMPLEMENT**: For each service, identify all @Transactional mutation methods and add `auditService.log()` calls
- **MIRROR**: Same pattern as Task 4 and 5
- **IMPORTS**: Same as Task 4
- **GOTCHA**: AdminSubmissionService has requeue/delete — both should be audited. AdminForumService may have hidden/bulk operations
- **VALIDATE**: Spot-check each service with a manual API call

### Task 7: Add Audit Logging to Permission Changes

- **ACTION**: Add audit logging to `PermissionServiceImpl` for grant/revoke operations
- **IMPLEMENT**: Log permission changes with target user ID and permission details
- **MIRROR**: Same pattern
- **GOTCHA**: Permission service may be called internally by other services — ensure we don't double-log
- **VALIDATE**: Check that granting a role creates a GRANT_PERMISSION audit record

### Task 8: Add Audit Logging to User Profile Updates

- **ACTION**: Add audit logging to `UserServiceImpl.updateCurrentUser()`
- **IMPLEMENT**: This is a USER-facing operation, not admin, but it's state-mutating. Log as UPDATE_USER with performer = user themselves
- **MIRROR**: Same pattern
- **GOTCHA**: Don't log sensitive fields like password. Redact email if desired
- **VALIDATE**: Update profile via console frontend, verify audit log created

### Task 9: Update Frontend i18n for New Action/Entity Types

- **ACTION**: Add new action types and entity types to both zh-CN and en-US audit i18n modules
- **IMPLEMENT**: Add entries for all constants defined in Task 1
- **MIRROR**: Follow existing `audit.ts` structure
- **IMPORTS**: N/A
- **GOTCHA**: Ensure action types in frontend filters match backend exactly (case-sensitive)
- **VALIDATE**: Switch language in management UI, verify labels display correctly

### Task 10: Update Frontend AuditLogsView Filters

- **ACTION**: Add new `<SelectItem>` options for additional entity types and actions
- **IMPLEMENT**: Update `actionFilter` and `entityTypeFilter` `<Select>` components in `AuditLogsView.vue`
- **MIRROR**: Follow existing SelectItem pattern in the file
- **GOTCHA**: Don't hardcode the list — ideally derive from a shared constants file, but the frontend currently hardcodes. For now, match the backend constants
- **VALIDATE**: Open audit logs view, verify all new filter options appear and filter correctly

---

## Testing Strategy

### Unit Tests

| Test | Input | Expected Output | Edge Case? |
|---|---|---|---|
| `AuditAspect.auditAround` with @Audited method | Mock service method | AuditService.log() called once | Yes — null performer |
| `AdminUserServiceImpl.banUser` | valid user ID | User banned + audit log created | Yes — already banned user |
| `AdminUserServiceImpl.bulkBan` | list of 3 user IDs | 3 audit logs + 1 summary | Yes — mixed success/failure |
| `AdminUserServiceImpl.resetPassword` | user ID, new password | Audit log WITHOUT password field | Yes — password never in JSON |
| `AuditActionUtil` constants | N/A | All referenced constants exist | No |

### Edge Cases Checklist

- [ ] Null performer (system action / unauthenticated) — log with "system" or skip
- [ ] Bulk operations with partial failures — log each item individually
- [ ] Concurrent updates — oldValues may be stale (acceptable for audit trail, not transactional)
- [ ] Large JSON payloads for old/new values — Jackson may fail; set max depth or use DTOs
- [ ] Password/credential fields — explicitly excluded from serialization
- [ ] Entity not found before update — gracefully skip oldValues capture
- [ ] IP behind proxy — use X-Real-IP header first, fallback to remoteAddr

---

## Validation Commands

### Static Analysis
```bash
cd backend-spring && ./mvnw compile -q
```
EXPECT: Zero compilation errors

### Unit Tests
```bash
cd backend-spring && ./mvnw test -Dtest="*AuditAspectTest*,*AdminUserServiceImplTest*"
```
EXPECT: All tests pass

### Full Test Suite
```bash
cd backend-spring && ./mvnw test
```
EXPECT: No regressions

### Integration Validation
```bash
# Start backend
cd backend-spring && ./mvnw spring-boot:run -Dmaven.test.skip=true

# In another terminal, trigger admin action and verify
curl -X POST http://localhost:9001/admin/users/ban \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"id":"user123","reason":"test"}'

# Query audit logs
curl "http://localhost:9001/admin/audit/logs?action=BAN_USER" \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```
EXPECT: Response contains at least one BAN_USER audit record

### Frontend Validation
```bash
cd management && pnpm dev
```
EXPECT: Audit logs view loads, filters work, detail drawer shows old/new values

### Manual Validation
- [ ] Ban a user from admin panel → verify audit log appears
- [ ] Update a problem → verify audit log shows changed fields
- [ ] Export audit logs as CSV → verify data integrity
- [ ] Switch frontend language → verify action/entity labels translate

---

## Acceptance Criteria

- [ ] All tasks completed
- [ ] `AuditActionUtil` defines all action and entity constants
- [ ] `@Audited` annotation exists and is processed by `AuditAspect`
- [ ] All admin mutation services log audit records
- [ ] User profile update creates audit record
- [ ] Permission grant/revoke creates audit record
- [ ] Frontend i18n includes all new action/entity types
- [ ] Frontend filters include all new action/entity types
- [ ] No passwords or credentials appear in audit log JSON
- [ ] All validation commands pass
- [ ] Tests written and passing
- [ ] No type errors
- [ ] No lint errors
- [ ] Audit logs view shows real data from backend

## Completion Checklist

- [ ] Code follows discovered patterns (AOP, service layer, constants)
- [ ] Error handling matches codebase style (BusinessException for errors)
- [ ] Logging follows codebase conventions (slf4j, debug for audit creation)
- [ ] Tests follow test patterns (JUnit 5, Mockito)
- [ ] No hardcoded values (use AuditActionUtil constants)
- [ ] Documentation updated (no new docs needed, inline comments sufficient)
- [ ] No unnecessary scope additions (no retention policies, no webhooks)
- [ ] Self-contained — a developer with this plan needs no further research

## Risks

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Audit logging slows down bulk operations | Medium | Medium | Use `@Async` on AuditService.log() or fire-and-forget with application events |
| Jackson serialization of entities fails (circular refs) | Medium | High | Use `ObjectMapper` with `SerializationFeature.FAIL_ON_EMPTY_BEANS` disabled; serialize DTOs instead of entities |
| Sensitive data (passwords) leaked in audit JSON | Low | Critical | Explicitly blacklist password fields before serialization; write unit test asserting no password in JSON |
| Double-logging from AOP + manual calls | Medium | Medium | Use AOP only for simple cases; manual calls for complex admin operations; never both on same method |
| Audit log table grows unbounded | Medium | Medium | Out of scope for this plan; document as follow-up for retention policy |
| Frontend/backend constant mismatch | Medium | Medium | Keep constants in sync; add integration test verifying all backend actions have i18n keys |

## Notes

- The existing `AuditServiceImpl.log()` method is synchronous and inserts directly. For high-traffic operations (if any are added later), consider making it `@Async`.
- The `audit_logs` table already has indexes on `performer_id`, `entity_type+entity_id`, and `created_at`, which should handle the query patterns from the admin panel.
- The frontend's `AuditLogDetailDrawer` displays `oldValues` and `newValues` as formatted JSON — the backend should ensure these are clean Map structures.
- For admin operations that use `LambdaUpdateWrapper` (MyBatis-Plus), you cannot easily capture old state via AOP because the wrapper doesn't contain the entity ID in a standard way. These MUST use manual `auditService.log()` calls in the service method.
- The `entityId` field in the audit log is a String. For numeric IDs (like problems which use Long), convert to String before logging.
- This plan intentionally avoids modifying the `console` (user-facing) frontend — audit is an admin-only feature.
- There is a separate existing plan `audit-granularity-alignment.plan.md` that handles frontend-backend API contract alignment. This plan is complementary: it focuses on populating the audit log with data, while the other plan focuses on ensuring the frontend can read it correctly.
