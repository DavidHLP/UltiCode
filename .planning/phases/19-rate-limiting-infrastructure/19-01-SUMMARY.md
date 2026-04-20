---
phase: 19-rate-limiting-infrastructure
plan: '01'
type: summary
subsystem: backend-spring
tags: [rate-limiting, spring-boot, http-headers]
dependency_graph:
  requires: []
  provides: [RATE-03]
  affects: [GlobalExceptionHandler]
tech_stack:
  added: [HttpHeaders.RETRY_AFTER]
  patterns: [exception-to-header mapping]
key_files:
  - path: backend-spring/src/main/java/com/ulticode/common/exception/GlobalExceptionHandler.java
    role: Added Retry-After header extraction for 429 responses
decisions:
  - id: regex-extraction
    rationale: Extract retry seconds from existing BusinessException message text via regex, avoiding new exception fields
    alternatives_considered:
      - "Add retryAfter() field to BusinessException — rejected: would require modifying exception class and RateLimitAspect"
      - "Store TTL in a thread-local — rejected: unnecessary complexity for one usage site"
  - id: inline-regex
    rationale: Inline Pattern/Matcher rather than static compiled Pattern — only used once per 429, performance not a concern
metrics:
  duration_minutes: <1
  completed_date: '2026-04-20'
---

# Phase 19 Plan 01: Add Retry-After Header to Rate Limit 429 Responses

**One-liner:** HTTP 429 responses now include a `Retry-After` header with the number of seconds until the rate limit resets.

## Objective

Add a `Retry-After` header to HTTP 429 responses so clients can automatically retry after the correct interval, closing the RATE-03 gap in the rate limiting infrastructure.

## Tasks Executed

| # | Name | Commit | Files |
|---|------|--------|-------|
| 1 | Add Retry-After header to rate limit 429 responses | 78639dc23 | GlobalExceptionHandler.java |

## What Was Done

Modified `GlobalExceptionHandler.handleBusinessException()` to detect `HttpStatus.TOO_MANY_REQUESTS` and extract the retry interval from the exception message using regex `(\d+) seconds?`, then add it as `HttpHeaders.RETRY_AFTER`.

**Before:**
```java
Result<Void> result = Result.error(ex.getCode(), ex.getMessage(), ex.getTraceId());
return ResponseEntity.status(ex.getHttpStatus()).body(result);
```

**After:**
```java
Result<Void> result = Result.error(ex.getCode(), ex.getMessage(), ex.getTraceId());

HttpStatus status = ex.getHttpStatus();
ResponseEntity.BodyBuilder responseBuilder = ResponseEntity.status(status);

if (ex.getHttpStatus() == HttpStatus.TOO_MANY_REQUESTS) {
    String message = ex.getMessage();
    java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(\\d+) seconds?").matcher(message);
    if (matcher.find()) {
        responseBuilder.header(HttpHeaders.RETRY_AFTER, matcher.group(1));
    }
}

return responseBuilder.body(result);
```

The `RateLimitAspect` already embeds the TTL in the exception message (e.g., `"Rate limit exceeded. Please try again in 60 seconds."`), so the regex extracts that value without requiring changes to the aspect or exception class.

## Acceptance Criteria

- [x] `grep -n "RETRY_AFTER" GlobalExceptionHandler.java` returns match at line 51
- [x] `grep -n "TOO_MANY_REQUESTS" GlobalExceptionHandler.java` returns match at line 47
- [x] `grep -n "HttpHeaders" GlobalExceptionHandler.java` returns 2 matches (import + usage)
- [x] `grep -n "matcher" GlobalExceptionHandler.java` returns 3 matches
- [x] `./mvnw compile -q` succeeds with exit code 0

## Deviations from Plan

None. Plan executed exactly as written.

## Verification

```bash
curl -X POST http://localhost:9001/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"test","password":"test"}' \
  -c /tmp/cookies.txt

# After hitting rate limit, response includes:
# Retry-After: 60
```

## Threat Flags

None.

## Self-Check: PASSED

- Commit `78639dc23` exists
- `GlobalExceptionHandler.java` modified with Retry-After header logic
- Compilation succeeds
- All 5 grep-based acceptance criteria verified
