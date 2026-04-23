---
status: resolved
trigger: "GET http://localhost:9001/admin/problems?sortOrder=desc&page=1&limit=10 500 (Internal Server Error) 修复异常"
created: "2026-04-22"
updated: "2026-04-24"
resolution_date: "2026-04-24"
symptoms:
  expected_behavior: "GET /admin/problems should return paginated problem data (200)."
  actual_behavior: "Request returns 500 Internal Server Error."
  error_messages: "Browser/network log: GET http://localhost:9001/admin/problems?sortOrder=desc&page=1&limit=10 500 (Internal Server Error)."
  timeline: "Not provided."
  reproduction: "Call GET /admin/problems with query params: sortOrder=desc&page=1&limit=10."

## Root Cause
MyBatis `@Results` setter-based mapping was used with immutable `ProblemTagDTO` Java record (which has no setters), causing `no setter for property problemId` and 500 in `/admin/problems`.

## Fix Applied
Changed `ProblemMapper.selectTagsByProblemIds` result mapping from `@Results` to constructor-based `@ConstructorArgs`/`@Arg` for record DTO fields.

**File changed:** `backend-spring/src/main/java/com/ulticode/modules/problem/mapper/ProblemMapper.java`

## Verification
After applying mapper annotation fix and restarting backend, authenticated GET `/admin/problems?sortOrder=desc&page=1&limit=10` returns HTTP 200 and valid data payload.

---
_Resolved: 2026-04-24 during v3.0 milestone close — same root cause as `admin-problems-500`_
