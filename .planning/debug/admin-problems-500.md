---
name: admin-problems-500
description: Debug GET /admin/problems 500 Internal Server Error
status: resolved
trigger: "request.ts:344 GET http://localhost:9001/admin/problems?sortOrder=desc&page=1&limit=10 500 (Internal Server Error)"
created: 2026-04-22
updated: 2026-04-24
resolution_date: "2026-04-24"
symptoms:
  expected: "Problem list should return paginated problems with 200 OK"
  actual: "500 Internal Server Error"
  error_messages: "500 Internal Server Error"
  timeline: "Issue reported 2026-04-22"
  reproduction: "GET /admin/problems?sortOrder=desc&page=1&limit=10"
  request_details: "request.ts:344, sortOrder=desc, page=1, limit=10"
---

## Root Cause

MyBatis `@Results` setter-based mapping was used with immutable `ProblemTagDTO` Java record, causing `no setter for property problemId` and 500 in `/admin/problems`.

## Fix Applied

Changed `ProblemMapper.selectTagsByProblemIds` result mapping from `@Results` to constructor-based `@ConstructorArgs`/`@Arg` for record DTO fields.

**File changed:** `backend-spring/src/main/java/com/ulticode/modules/problem/mapper/ProblemMapper.java`

**Verification:** After applying mapper annotation fix and restarting backend, authenticated GET `/admin/problems?sortOrder=desc&page=1&limit=10` returns HTTP 200 and valid data payload.

## Duplicate Note

This is the same root cause as `get-http-localhost-9001-admin`. Both debug sessions tracked the same `/admin/problems` 500 error.

---
_Resolved: 2026-04-24 during v3.0 milestone close_
