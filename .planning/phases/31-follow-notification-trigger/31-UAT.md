# Phase 31: Follow Notification Trigger — UAT

**Phase:** 31
**Tested by:** Claude (API-driven verification)
**Date:** 2026-04-21
**Status:** PASS (with fixes applied)

---

## Test Results

### T-01: First follow creates notification

| Field | Expected | Actual |
|-------|----------|--------|
| type | FOLLOW | FOLLOW ✓ |
| category | COMMUNICATION (DB enum) | COMMUNICATION ✓ |
| title | "{followerUsername} followed you" | "follower99 followed you" ✓ |
| body | "" (empty) | "" ✓ |
| link | "/profile/{followerUsername}" | "/profile/follower99" ✓ |
| isRead | false | false ✓ |
| persisted to DB | yes | yes ✓ |

**Result:** PASS

---

### T-02: Re-follow does NOT create duplicate notification

| Field | Expected | Actual |
|-------|----------|--------|
| Notification count after re-follow | 1 (unchanged) | 1 ✓ |

**Result:** PASS

---

### T-03: Notification failure does not break follow action

- Follow API returned `{"code":0,"message":"success"}` even when category mismatch would cause DB constraint violation (tested with "social" category — follow succeeded, no crash)

**Result:** PASS (fire-and-notify pattern works)

---

## Bugs Found & Fixed During Verification

### Bug 1: Wrong username in notification title

- **Problem:** Title used `target.getUsername()` (user being followed), but should show the follower's username
- **Fix:** Fetch `currentUser` inside the idempotent block and use `currentUser.getUsername()`
- **Severity:** HIGH (wrong content displayed to user)

### Bug 2: Invalid category "social"

- **Problem:** `category = "social"` not in DB enum (only COMMUNICATION, MARKETING, SECURITY, SYSTEM)
- **Fix:** Changed to `"COMMUNICATION"`
- **Severity:** HIGH (notification insert would fail with DB constraint violation)
- **Root cause:** CONTEXT.md D-05 said "social" based on Phase 28 achievement categories, but notification category enum is different from achievement category enum

---

## Pre-existing Blocker

`SubmissionServiceImplTest` and `SubmissionServiceImplIT` have pre-existing compilation errors (missing `AchievementTriggerService` constructor parameter). This blocked Maven test-compile. Not related to Phase 31.

---

## Verification Commands

```bash
# Clean test users
docker exec ulticode-mysql mysql -u ulticode -pulticode ulticode -e "DELETE FROM user_follows WHERE follower_id = '6dd637ae5a7c4489b33879f2faa07060'; DELETE FROM notifications WHERE user_id = '11212c3739c744f4a1694e99c946c746';"

# Register follower + target, get IDs, follow, check notifications
```

