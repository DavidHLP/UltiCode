# Codebase Concerns

**Analysis Date:** 2026-04-22

## Resolved Issues (Historical - Past 24 Hours)

Documented for awareness. These were discovered and fixed during active development.

### Login 500 Errors (Resolved 2026-04-16)

**Issue:** All `/auth/login` requests returned HTTP 500 due to `SQLSyntaxErrorException: Unknown column 'password_reset_token_hash'`

**Root Cause:** V20 migration (add password reset columns) was not applied. `User` entity referenced columns via `@TableField` that did not exist in the `users` table.

**Fix:** Applied V20 migration manually and registered in `flyway_schema_history`.

**Files:** `backend-spring/src/main/java/com/ulticode/modules/user/entity/User.java:158,164`

---

### Achievement Endpoints 500 Errors (Resolved 2026-04-19)

**Issue:** `GET /achievements/my`, `/achievements/user/me`, `/achievements/points` all returned `code=50000, message="Unknown error"`

**Root Cause:** `achievements` and `user_achievements` MySQL tables did not exist. `AchievementServiceImpl.getUserAchievements()` called `achievementMapper.findAllActive()` which threw `BadSqlGrammarException`.

**Fix:** Created and applied `V22__achievement_schema.sql` migration.

**Files:** `db-manager/migrations/V22__achievement_schema.sql`

---

### CsrfValidationFilter Bypassing Exception Handler (Resolved 2026-04-19)

**Issue:** `POST /admin/problems/bulk` returned HTTP 500 with HTML body instead of JSON when CSRF was missing/invalid.

**Root Cause:** `CsrfValidationFilter` (servlet filter in Spring Security chain) threw `BusinessException` directly. Since it ran before `DispatcherServlet`, `@RestControllerAdvice` never caught it. Tomcat rendered default HTML error page.

**Fix:** Rewrote filter to write JSON error response directly via `HttpServletResponse` with proper HTTP 403 status codes.

**Files:** `backend-spring/src/main/java/com/ulticode/security/csrf/CsrfValidationFilter.java`

---

### Flyway Migration Sequencing (Resolved 2026-04-21)

**Issue:** `db-manager/.venv/bin/python -m db_manager.cli migrate` failed with "Validate failed: Detected resolved migration not applied to database: 10.1, 21"

**Root Cause:** V26 (`follow_schema.sql`) version number was lower than installed V99, causing Flyway to reject it. The `user_follows` table existed but Flyway had no record.

**Fix:** Renamed V26 to V100, manually deleted conflicting Flyway history record, inserted correct V100 record.

---

## Resolved This Week

### Achievement Async Processing (PITFALL-01) - RESOLVED

**Status:** Complete (Phase 36)

**Previous Issue:** Achievement checking ran synchronously on every submission, blocking user-facing API.

**Current Implementation:** `AchievementCheckListener.java:19-20`:
```java
@Async
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
```

**Verification:** Achievements now fire after transaction commits via async event listener.

---

## Pending Requirements (from REQUIREMENTS.md)

### Admin Forum Stats Hardcoded (BUG-01 / PITFALL-02)

**Status:** Pending (Phase 37)

**Issue:** Admin forum statistics return hardcoded zero values instead of real data.

**Files:** `backend-spring/src/main/java/com/ulticode/modules/admin/service/impl/AdminForumServiceImpl.java:276-278`

```java
vo.setCommentCount(0); // TODO: Query from forum_comments table
vo.setUpvotes(0); // TODO: Query from forum_votes table
vo.setDownvotes(0); // TODO: Query from forum_votes table
```

**Recent Activity:** Commit `48b3c2cc2` (feat(37): replace hardcoded forum stats with real DB queries) was pushed to main. May be resolved in working tree.

---

### springdoc Incompatibility (DEPS-01) - PENDING

**Issue:** springdoc 2.7.0 incompatible with Spring Boot 3.2.5 (missing `LiteWebWebJarsResourceResolver` class).

**Current State:** Already downgraded to 2.6.0 (documented in "Already Resolved"). However, DEPS-01 remains open pending upgrade to 3.x LTS when available.

**Files:** `backend-spring/pom.xml`

---

### CI Flyway URL (DEPS-02) - PENDING

**Issue:** CI workflow uses incorrect Flyway download URL (Redgate official URL required).

**Impact:** CI pipeline failures related to database migration.

---

## Dependency Risks

### DEPS-03: springdoc 3.x Upgrade (V2 DEFERRED)

**Status:** Out of scope until LTS release available.

---

## Performance Concerns

### PERF-01: Achievement N+1 Query (V2 DEFERRED)

**Status:** Deferred to v2 roadmap.

**Issue:** Achievement queries may have N+1 patterns when loading user achievements with related data.

---

### PERF-02: Follow System Index (V2 DEFERRED)

**Status:** Deferred to v2 roadmap.

**Issue:** `user_follows` table may lack composite index for efficient follow/follower queries.

**Current Indexes:**
```sql
INDEX idx_follower_id (follower_id)
INDEX idx_following_id (following_id)
```

**Missing:** Composite index for `isFollowing` check queries.

**Files:** `backend-spring/src/main/java/com/ulticode/modules/follow/mapper/FollowMapper.java`

---

### Admin Content Analytics Tag Loop N+1 (NOTED IN CODE)

**Issue:** `AdminContentAnalyticsServiceImpl.java:78`:
```java
// NOTE: N+1 issue exists in the tag loop below (per-problem submission count queries).
```

---

## Testing Gaps

### MISS-01: JaCoCo Coverage Thresholds Too Low

**Issue:** JaCoCo configured with 50% LINE and 40% BRANCH minimums, below the 80% project standard.

**Current Configuration** (`backend-spring/pom.xml:282-290`):
```xml
<counter>LINE</counter>
<minimum>0.50</minimum>   <!-- Should be 0.80 -->
<counter>BRANCH</counter>
<minimum>0.40</minimum>   <!-- Should be 0.80 -->
```

**Excluded from Coverage:** All Mapper, Entity, DTO/VO, Config, Properties classes.

**Impact:** 80% coverage mandate in `REQUIREMENTS.md` is not enforced.

---

### MISS-02: Rate Limiting E2E Tests

**Status:** Out of scope per REQUIREMENTS.md.

---

## Security Considerations

### Positive Security Measures

- BCrypt password hashing (`SecurityConfig.java:165`)
- JWT secret validation at startup (`JwtProperties.java`)
- Weak JWT secret blacklist check (`EnvValidationConfig.java:26-29`)
- CSRF filter returns structured JSON errors (resolved 2026-04-19)
- Password reset tokens stored as BCrypt hashes (`PasswordResetService.java:62`)

### Areas Requiring Attention

**SEC-02: System.out.println in Code Execution**
- File: `backend-spring/src/main/java/com/ulticode/modules/submission/service/impl/CodeExecutionHelperImpl.java:252`
- Risk: Submission output could leak to stdout logs

**SEC-03: console.error in Production Vue**
- Multiple Vue components in `console/` and `management/`
- Risk: Debug output in production browser consoles

---

## Maintainability Issues

### Large Files Exceeding Best Practices

**Java (500+ lines):**
- `ForumServiceImpl.java` (693 lines)
- `SubmissionServiceImpl.java` (682 lines)
- `CodeExecutionService.java` (643 lines)
- `ContestServiceImpl.java` (626 lines)
- `ModerationServiceImpl.java` (578 lines)

**Frontend (500+ lines):**
- `useContestSocket.ts` (608 lines)
- `contest.ts` (559 lines)
- `columns.ts` (management, 533 lines)
- `problem-list.ts` (528 lines)
- `useCodeTemplates.ts` (527 lines)

---

### Inconsistent Exception Response Format

**Issue:** `GlobalExceptionHandler` returns `code=50000, message="Unknown error"` for database exceptions without distinguishing between "table missing", "connection failed", and "constraint violation".

---

### Manual Migration Intervention History

**Issue:** Multiple incidents required manual SQL execution outside of Flyway (`V20`, `V22`, `V100` fixes).

**Root Cause:** Flyway's strict validation against checksum/history causes failures when migrations are modified post-application or version numbering conflicts occur.

---

## Fragile Code Patterns

### JWT Token Provider Null Returns

**Files:** `JwtAuthenticationFilter.java:127,137,151`, `CsrfService.java:70,76,87`

**Risk:** Silent authentication failures could cause intermittent "logged out" UX.

---

### Redis Service Null Returns

**File:** `RedisService.java:84,103,107,299,504,520`

**Risk:** Cache misses returning null could be confused with errors.

---

### Volatile Counter in Monitoring

**File:** `MonitoringServiceImpl.java:58` - `private volatile long queryCount = 0;`

**Risk:** `volatile` is insufficient for atomic counter increments.

---

## Build Configuration Issues

### Test Skip in Production Scripts

Both `ecosystem.config.cjs` and `start-backend.sh` use `-Dmaven.test.skip=true`, meaning tests are not run during service startup.

---

## Summary of Priorities

| Priority | ID | Issue | Status |
|----------|-----|-------|--------|
| HIGH | BUG-01 | Admin Forum Stats hardcoded | Phase 37 (in progress) |
| HIGH | DEPS-01 | springdoc compatibility | Pending 3.x LTS |
| HIGH | DEPS-02 | CI Flyway URL broken | Pending |
| MEDIUM | MISS-01 | JaCoCo thresholds 50%/40% | Should be 80% |
| MEDIUM | PERF-02 | user_follows missing composite index | V2 deferred |
| LOW | DEPS-03 | springdoc 3.x upgrade | V2 deferred |
| LOW | PERF-01 | Achievement N+1 | V2 deferred |

---

## Already Resolved (Historical Reference)

These issues were identified and fixed during earlier development cycles:

- **ESLint version conflict:** ESLint 10.x incompatible with @typescript-eslint/utils 8.x - Fixed by downgrading to eslint ^9.30.1
- **vitest setupFiles reference:** References non-existent `console/src/test/setup.ts` - Fixed by removing setupFiles
- **recommend-api not installed:** CI Backend Build failed - Fixed by ensuring proper build order
- **OAuthService Spring 6.x compatibility:** Fixed for Spring Framework 6.x compatibility
- **springdoc LiteWebJarsResourceResolver:** Downgraded from 2.7.0 to 2.6.0
- **CI lockfile issues:** Multiple CI fixes applied and verified
- **PM2 env parsing:** Custom .env parser fragility (TD-01 from prior doc)
- **Maven build order:** recommendation must be installed before backend-spring (TD-02 from prior doc)
- **Dubbo configuration complexity:** enable-empty-protection workaround documented (TD-03 from prior doc)

---

*Concerns audit: 2026-04-22*
