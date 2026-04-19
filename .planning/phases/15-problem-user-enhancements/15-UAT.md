---
status: resolved
phase: 15-problem-user-enhancements
source:
  - .planning/phases/15-problem-user-enhancements/15-01-SUMMARY.md
  - .planning/phases/15-problem-user-enhancements/15-02-SUMMARY.md
started: 2026-04-19T11:52:00+08:00
updated: 2026-04-19T11:52:00+08:00
---

## Current Test

[testing complete]

## Tests

### 1. Random Problem Endpoint
expected: GET /problems/random returns a single random published problem as JSON with all ProblemVO fields (id, title, difficulty, acceptanceRate, tags, etc.). No authentication required. Each call may return a different problem.
result: pass

### 2. Acceptance Rate in ProblemVO
expected: Problem list/detail responses include an "acceptance_rate" field computed from submissions data. Value is a decimal between 0 and 100.
result: pass

### 3. User Stats — globalRank
expected: GET /users/{id}/stats returns an Integer "globalRank" field representing the user's rank based on total AC submissions. Authenticated requests only.
result: issue
reported: "globalRank returns null for u-admin-001. User has no entry in global_rankings table (which is based on contest rating, not AC submissions). Field correctly returns null but test expectation may not match actual implementation."
severity: major

### 4. User Stats — acceptanceRate
expected: GET /users/{id}/stats returns a Double "acceptanceRate" field (0.0–100.0) computed from user's total AC vs total submissions.
result: pass

### 5. User Stats — submissionCount
expected: GET /users/{id}/stats returns a Long "submissionCount" field representing the user's total number of submissions.
result: pass

### 6. Admin Bulk Problem Publish/Unpublish
expected: POST /admin/problems/bulk with { "action": "publish", "ids": [...] } marks specified problems as published. Requires ADMIN role. Returns bulk result with affected count.
result: pass

### 7. Admin Bulk Problem Delete
expected: POST /admin/problems/bulk with { "action": "delete", "ids": [...] } removes specified problems. Requires ADMIN role. Returns bulk result with affected count.
result: pass

### 8. Admin Bulk Problem Edit
expected: POST /admin/problems/bulk with { "action": "edit", "ids": [...], "data": { ... } } updates problem fields. CreateProblemDTO fields (summary, content, examples, constraints, hints, languages, tags) are accepted. Requires ADMIN role.
result: pass

### 9. Achievement Alias /achievements/my
expected: GET /achievements/my (no auth or authenticated) returns the same response as GET /achievements/user/me — a list of current user's achievements.
result: pass

### 10. Achievement Alias /achievements/points
expected: GET /achievements/points (no auth or authenticated) returns the same response as GET /achievements/user/me/points — the current user's points summary.
result: pass

## Summary

total: 10
passed: 9
issues: 1
pending: 0
skipped: 0
blocked: 0

## Gaps

- truth: "GET /users/{id}/stats returns an Integer globalRank based on total AC submissions"
  status: resolved
  reason: "findGlobalRankByUserId SQL replaced with AC-count-based ranking. GET /users/u-admin-001/stats now returns globalRank=261 (non-null). Verification: curl output confirmed."
  severity: major
  test: 3
