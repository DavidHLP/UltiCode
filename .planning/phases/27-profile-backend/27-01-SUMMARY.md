---
phase: "27"
plan: "01"
subsystem: "profile-backend"
tags: ["backend", "user", "profile", "social"]
dependency_graph:
  requires: []
  provides: ["PROFILE-01", "PROFILE-03"]
  affects: ["frontend-console", "frontend-management"]
tech_stack:
  added: ["ProfileVO DTO"]
  patterns: ["Profile aggregation", "MultipartFile upload", "FollowMapper integration"]
key_files:
  created:
    - "backend-spring/src/main/java/com/ulticode/modules/user/dto/ProfileVO.java"
  modified:
    - "backend-spring/src/main/java/com/ulticode/modules/user/service/UserService.java"
    - "backend-spring/src/main/java/com/ulticode/modules/user/service/impl/UserServiceImpl.java"
    - "backend-spring/src/main/java/com/ulticode/modules/user/controller/UserController.java"
decisions:
  - "FollowMapper.countByFollowingId maps to followerCount (how many follow this user)"
  - "FollowMapper.countByFollowerId maps to followingCount (how many this user follows)"
  - "ErrorCode.UNKNOWN_ERROR used for file storage failures (no INTERNAL_SERVER_ERROR in enum)"
  - "Avatar stored at uploads/avatars/{uuid}.{ext}, served via /uploads/avatars/ URL"
metrics:
  duration: "~4 minutes"
  completed_date: "2026-04-21"
---

# Phase 27 Plan 01: Profile Backend Summary

**Wave 1: Profile Backend Core**

Implemented user profile viewing, editing, and avatar upload functionality.

## One-liner

ProfileVO aggregating user data + stats + social counts with GET /users/{id}/profile endpoint and POST /users/me/avatar file upload.

## Completed Tasks

| # | Task | Commit |
|---|------|-------|
| 1 | Create ProfileVO.java with 17 fields and fromUser() factory | e961369c2 |
| 2 | Add getUserProfile/uploadAvatar to UserService interface | d69614841 |
| 3 | Implement getUserProfile and uploadAvatar in UserServiceImpl | e67d68a65 |
| 4 | Add GET /users/{id}/profile and POST /users/me/avatar endpoints | 521160d07 |

## What Was Built

**GET /users/{id}/profile** -- Returns `ProfileVO` with 17 fields:
- Identity: id, username, name, avatar, joinedAt, preferredLanguage
- Bio: bio, company, location, website
- Stats: totalSolved, submissionCount, globalRank, acceptanceRate
- Social: followerCount, followingCount, achievementCount

**POST /users/me/avatar** -- Accepts MultipartFile, validates image type, stores to `uploads/avatars/{uuid}.{ext}`, updates user.avatar in DB, returns URL. Rate-limited to 10 requests/minute.

## Deviations from Plan

**None - plan executed exactly as written.**

## Deviations (Auto-fixed Issues)

**1. [Rule 3 - Blocking] Missing ProfileVO import**
- **Found during:** Compilation check after Task 1
- **Issue:** ProfileVO.fromUser() used User entity without importing it
- **Fix:** Added `import com.ulticode.modules.user.entity.User;` to ProfileVO.java
- **Files modified:** ProfileVO.java
- **Commit:** e961369c2 (amended)

**2. [Rule 3 - Blocking] Missing ProfileVO import in interface**
- **Found during:** Compilation check after Task 2
- **Issue:** UserService interface referenced ProfileVO without importing it
- **Fix:** Added `import com.ulticode.modules.user.dto.ProfileVO;` to UserService.java
- **Files modified:** UserService.java
- **Commit:** d69614841 (amended)

**3. [Rule 3 - Blocking] INTERNAL_SERVER_ERROR not in ErrorCode enum**
- **Found during:** Compilation check after Task 3
- **Issue:** ErrorCode.INTERNAL_SERVER_ERROR does not exist in the exception enum
- **Fix:** Replaced with ErrorCode.UNKNOWN_ERROR (code 50000, HTTP 500)
- **Files modified:** UserServiceImpl.java
- **Commit:** e67d68a65 (amended)

## Acceptance Criteria Status

- [x] ProfileVO has 17 fields (id, username, name, avatar, bio, company, location, website, joinedAt, preferredLanguage, totalSolved, submissionCount, globalRank, acceptanceRate, followerCount, followingCount, achievementCount)
- [x] ProfileVO has static fromUser() factory method
- [x] Class uses @Data + @JsonInclude(NON_NULL)
- [x] getUserProfile and uploadAvatar added to UserService interface
- [x] FollowMapper injected via constructor in UserServiceImpl
- [x] getUserProfile implementation aggregates User + UserStatsDTO + follow counts
- [x] uploadAvatar stores to uploads/avatars with UUID filename
- [x] GET /users/{id}/profile endpoint added to UserController
- [x] POST /users/me/avatar with @RateLimit(10/min) added to UserController
- [x] Code compiles successfully

## Must-Haves Status

- [x] GET /users/{id}/profile returns ProfileVO with all 17 fields
- [x] ProfileVO includes stats (totalSolved, submissionCount, globalRank, acceptanceRate) from UserStatsDTO
- [x] ProfileVO includes followerCount/followingCount from FollowMapper (graceful fallback to 0)
- [x] ProfileVO includes achievementCount (placeholder 0)
- [x] POST /users/me/avatar stores file to uploads/avatars/{uuid}.{ext}, updates user.avatar, returns URL

## Threat Flags

None - no new security surface beyond the plan scope.

## Commits

```
e961369c2 feat(phase-27): add ProfileVO DTO for user profile endpoint
d69614841 feat(phase-27): add getUserProfile and uploadAvatar to UserService interface
e67d68a65 feat(phase-27): implement getUserProfile and uploadAvatar in UserServiceImpl
521160d07 feat(phase-27): add profile and avatar endpoints to UserController
```

## Self-Check

- [x] All 4 files created/modified exist
- [x] All 4 commits found in git log
- [x] Compilation succeeds
- [x] All acceptance criteria met
