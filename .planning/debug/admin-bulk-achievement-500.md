---
name: admin-bulk-achievement-500
status: resolved
trigger: "/admin/problems/bulk returns HTTP 500 when called with POST, and /achievements/my, /achievements/user/me, /achievements/points all return HTTP 500 Unknown error"
created: 2026-04-19T17:12:00+08:00
updated: 2026-04-19T17:26:00+08:00
---

## Symptoms

- **Expected behavior:**
  - POST /admin/problems/bulk with {action:"publish",ids:[...]} should mark problems as published and return bulk result with affected count
  - GET /achievements/my should return same response as GET /achievements/user/me (list of user achievements)
  - GET /achievements/points should return same response as GET /achievements/user/me/points (points summary)
- **Actual behavior:**
  - POST /admin/problems/bulk returns HTTP 500 Internal Server Error (HTML body)
  - GET /achievements/my returns {"code":50000,"message":"Unknown error"}
  - GET /achievements/user/me returns {"code":50000,"message":"Unknown error"}
  - GET /achievements/points returns {"code":50000,"message":"Unknown error"}
- **Error messages:** HTTP 500, code 50000 Unknown error, and HTML error pages
- **Timeline:** Discovered during Phase 15 UAT testing

## Root Cause

Two independent root causes identified:

### Root Cause 1: Missing achievement database tables

**Affected endpoints:** GET /achievements/my, GET /achievements/user/me, GET /achievements/points, GET /achievements/user/me/points

The `achievements` and `user_achievements` tables did not exist in the MySQL database. The `AchievementServiceImpl.getUserAchievements()` calls `achievementMapper.findAllActive()` which queries `SELECT * FROM achievements WHERE is_active = 1`, causing a `BadSqlGrammarException`. The `GlobalExceptionHandler.handleGenericException()` catches this and returns `code=50000, message="Unknown error"`.

**Evidence from logs:**
```
Table 'ulticode.achievements' doesn't exist
SQL: SELECT * FROM achievements WHERE is_active = 1 ORDER BY category ASC, tier ASC
```

**Fix:** Created migration `V22__achievement_schema.sql` with the two tables and applied it directly to MySQL (Flyway had a dangling V21 conflict). Tables `achievements` and `user_achievements` now exist with proper schema matching the entity definitions.

### Root Cause 2: CSRF filter bypasses exception handler

**Affected endpoint:** POST /admin/problems/bulk (and any state-changing endpoint)

`CsrfValidationFilter` (a servlet filter) throws `BusinessException` directly when CSRF validation fails. Since it runs in the Spring Security filter chain (before the DispatcherServlet), `@RestControllerAdvice` never catches it. The exception propagates to Tomcat, which renders a default HTML error page with HTTP 500 status.

**Evidence from logs:**
```
com.ulticode.common.exception.BusinessException: CSRF token is required
```

The filter also throws for invalid/expired CSRF tokens. The proper behavior should be a structured JSON error response matching the application API format.

**Fix:** Rewrote `CsrfValidationFilter` to write a JSON error response directly via `HttpServletResponse` instead of throwing `BusinessException`. The `writeErrorResponse()` method writes:
```json
{"code":40300,"message":"CSRF token is required","data":null,"traceId":"t-<timestamp>"}
```
to the response with HTTP 403 status. The same fix applies for invalid tokens.

## Resolution

root_cause: "Two independent issues: (1) The achievements/user_achievements MySQL tables were missing from the database schema. (2) CsrfValidationFilter threw BusinessException from a servlet filter, bypassing @RestControllerAdvice and producing HTML 500 pages instead of JSON."
fix: "1. Created and applied V22__achievement_schema.sql migration to add achievements and user_achievements tables. 2. Rewrote CsrfValidationFilter to write JSON error responses directly instead of throwing BusinessException."
verification: "All four achievement endpoints return 200 with valid JSON. Bulk publish/unpublish/delete all return 200 when provided with correct CSRF token. Missing CSRF token returns 403 with JSON."
files_changed:
  - db-manager/migrations/V22__achievement_schema.sql
  - backend-spring/src/main/java/com/ulticode/security/csrf/CsrfValidationFilter.java

## Specialist Review

No specialist review required -- both fixes are straightforward schema migrations and filter-level error handling changes.

## Evidence

- timestamp: 2026-04-19T17:19:13 - Achievement 500 error log: Table 'ulticode.achievements' doesn't exist
- timestamp: 2026-04-19T17:22:34 - Bulk 500 error log: BusinessException: CSRF token is required
- timestamp: 2026-04-19T17:25:00 - Verification: all endpoints return 200 with JSON responses
