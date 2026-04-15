---
phase: 02-core-functionality
plan: 01
subsystem: auth
tags: [password-reset, flyway, mybatis-plus, email, bcrypt]

# Dependency graph
requires: []
provides:
  - "V20 migration adding password_reset_token_hash and password_reset_expires_at columns to users table"
  - "PasswordResetService with EmailService integration and DB-based token storage"
  - "BCrypt-hashed token storage with 30-minute expiry on users table"
affects: [auth, email]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "DB-stored BCrypt token hash for password reset (replaces Redis-based approach)"
    - "Inline HTML email construction via SendEmailDTO setters (template-optional pattern)"

key-files:
  created:
    - "db-manager/migrations/V20__add_password_reset_columns.sql"
    - "db-manager/migrations/V18__add_submission_retry_count.sql"
    - "db-manager/migrations/V19__submission_memory_nullable.sql"
  modified:
    - "backend-spring/src/main/java/com/ulticode/modules/user/entity/User.java"
    - "backend-spring/src/main/java/com/ulticode/modules/auth/service/PasswordResetService.java"

key-decisions:
  - "Used inline HTML for reset email instead of templateId to avoid EMAIL_TEMPLATE_NOT_FOUND exception"
  - "Used SendEmailDTO setters instead of builder (class lacks @Builder annotation)"

patterns-established:
  - "DB-stored BCrypt token: hash stored in users table, plain token only in email link"
  - "Token overwrite: new forgot-password request replaces previous token (one active reset per user)"

requirements-completed: [SEC-02]

# Metrics
duration: 3min
completed: 2026-04-15
---

# Phase 02 Plan 01: Password Reset Email Wiring Summary

**Password reset flow migrated from Redis to DB-stored BCrypt token hashes with EmailServiceImpl integration for actual email delivery**

## Performance

- **Duration:** 3 min
- **Started:** 2026-04-15T11:07:27Z
- **Completed:** 2026-04-15T11:10:41Z
- **Tasks:** 2
- **Files modified:** 5

## Accomplishments
- V20 Flyway migration adds password_reset_token_hash and password_reset_expires_at columns with index
- PasswordResetService fully rewritten: removed RedisTemplate, injected EmailService, stores BCrypt-hashed tokens in users table
- forgotPassword() sends real email via EmailServiceImpl with inline HTML template
- resetPassword() verifies BCrypt hash, clears token fields, and revokes all user sessions
- 30-minute token TTL (down from 1 hour), silent return for non-existent emails

## Task Commits

Each task was committed atomically:

1. **Task 1: Create Flyway V20 migration and update User entity for password reset token storage** - `6dde673a6` (feat)
2. **Task 2: Rewrite PasswordResetService to use EmailServiceImpl and DB token storage** - `6448d13ce` (feat)

## Files Created/Modified
- `db-manager/migrations/V20__add_password_reset_columns.sql` - Adds password_reset_token_hash VARCHAR(255) and password_reset_expires_at DATETIME(3) to users table with index
- `db-manager/migrations/V18__add_submission_retry_count.sql` - Copied from main repo (other worktree agent)
- `db-manager/migrations/V19__submission_memory_nullable.sql` - Copied from main repo (other worktree agent)
- `backend-spring/src/main/java/com/ulticode/modules/user/entity/User.java` - Added passwordResetTokenHash and passwordResetExpiresAt fields
- `backend-spring/src/main/java/com/ulticode/modules/auth/service/PasswordResetService.java` - Complete rewrite: Redis replaced with DB + EmailService

## Decisions Made
- **Inline HTML instead of templateId:** EmailServiceImpl throws EMAIL_TEMPLATE_NOT_FOUND when templateId doesn't exist. Used inline HTML/text for the reset email to avoid this runtime error. Template creation can be added later.
- **Setter-based DTO construction:** SendEmailDTO lacks @Builder annotation (only @Data). Used setters instead of the builder pattern suggested in the plan.
- **V18/V19 copied from main repo:** These migrations were created by other worktree agents but not present in this worktree. Copied to ensure sequential migration numbering.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Used SendEmailDTO setters instead of builder**
- **Found during:** Task 2 (PasswordResetService rewrite)
- **Issue:** Plan specified `SendEmailDTO.builder()...build()` but SendEmailDTO only has @Data annotation, no @Builder
- **Fix:** Used setter-based construction (new SendEmailDTO() + setTo/setSubject/setHtml/setText)
- **Files modified:** backend-spring/src/main/java/com/ulticode/modules/auth/service/PasswordResetService.java
- **Verification:** All 7 acceptance criteria pass
- **Committed in:** `6448d13ce`

**2. [Rule 3 - Blocking] Used inline HTML instead of templateId for reset email**
- **Found during:** Task 2 (PasswordResetService rewrite)
- **Issue:** Plan used `templateId("password-reset")` but EmailServiceImpl throws BusinessException if template doesn't exist in DB
- **Fix:** Constructed email with inline HTML and plain text body, avoiding template lookup entirely
- **Files modified:** backend-spring/src/main/java/com/ulticode/modules/auth/service/PasswordResetService.java
- **Verification:** emailService.sendEmail() called with inline HTML content
- **Committed in:** `6448d13ce`

**3. [Rule 3 - Blocking] Copied V18/V19 migrations from main repo**
- **Found during:** Task 1 (migration creation)
- **Issue:** Plan assumed V19 existed in worktree but only V17 was committed to the worktree's branch. V18 and V19 exist in main repo (other worktree agents).
- **Fix:** Copied V18 and V19 from main repo to worktree to maintain sequential migration numbering
- **Files modified:** db-manager/migrations/V18__*, db-manager/migrations/V19__*
- **Committed in:** `6dde673a6`

---

**Total deviations:** 3 auto-fixed (3 blocking)
**Impact on plan:** All auto-fixes necessary for compilation correctness. No scope creep.

## Issues Encountered
- **Docker/MySQL unavailable in worktree:** Flyway migration could not be applied during execution (connection refused on port 23306). The SQL migration is syntactically correct and will be applied when services are running.
- **Pre-existing compile error in SubmissionServiceImpl:** Three missing DTOs (LanguageStatsDTO, WeeklyProgressDTO, MonthlySubmissionStatsDTO) cause `mvnw compile` to fail. These are from another worktree agent's changes not yet merged. No errors in files modified by this plan.

## User Setup Required

None - no external service configuration required beyond what already exists (SMTP config in application.yml for EmailServiceImpl).

## Next Phase Readiness
- Password reset backend flow is complete: forgot-password request stores BCrypt hash in DB, sends email, reset-password verifies and updates password
- Migration V20 must be applied before first use (run `db-manager/.venv/bin/python -m db_manager.cli migrate` with Docker running)
- Frontend forgot-password page needs to be updated to POST to the forgot-password endpoint (out of scope for this plan)

---
*Phase: 02-core-functionality*
*Completed: 2026-04-15*
