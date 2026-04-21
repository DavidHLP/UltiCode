---
phase: 29-social-frontend
plan: "01"
subsystem: api
tags: [backend, follow, achievement, profile, spring-boot]
key-files:
  created:
    - "backend-spring/src/main/java/com/ulticode/modules/follow/dto/FollowStatusDTO.java"
    - "backend-spring/src/main/java/com/ulticode/modules/follow/controller/FollowController.java"
    - "backend-spring/src/main/java/com/ulticode/modules/follow/service/FollowService.java"
    - "backend-spring/src/main/java/com/ulticode/modules/follow/service/impl/FollowServiceImpl.java"
    - "backend-spring/src/main/java/com/ulticode/modules/user/controller/UserController.java"
    - "backend-spring/src/main/java/com/ulticode/modules/user/service/UserService.java"
    - "backend-spring/src/main/java/com/ulticode/modules/user/service/impl/UserServiceImpl.java"
    - "backend-spring/src/main/java/com/ulticode/modules/achievement/controller/AchievementController.java"
  modified: []
  frontend_created:
    - "console/src/api/follow.ts"
    - "console/src/composables/useFollowStatus.ts"
    - "console/src/api/user.ts"
    - "console/src/router/index.ts"
    - "console/src/i18n/locales/en-US/personal.ts"
    - "console/src/i18n/locales/zh-CN/personal.ts"
key-decisions:
  - "GET /users/{id}/follow/status returns FollowStatusDTO with isFollowing boolean"
  - "UserService.getUserProfileByUsername resolves username to User then calls getUserProfile(user.getId())"
  - "AchievementController GET /user/{id} placed before /user/me to avoid path conflict"
  - "Frontend API/composable/route/i18n created by Wave 2 executor (29-02) since Wave 1 ran concurrently"
patterns-established:
  - "Follow status check via FollowMapper.exists() delegation"
  - "Username-to-profile resolution via findByUsername + getUserProfile composition"
requirements-completed:
  - PROFILE-02
  - FOLLOW-03
---

# Phase 29-01: Backend API Endpoints Summary

**Three backend API endpoints for social frontend: follow status check, username-to-profile lookup, and any-user achievement fetch**

## Performance

- **Started:** 2026-04-21T16:52:00Z
- **Completed:** 2026-04-21T16:52:00Z
- **Tasks:** 1
- **Files created:** 8 (backend) + 6 (frontend by Wave 2)

## Task Commits

1. **Task 1: Add three backend API endpoints** - `3ae41f7b9` (feat)

**Plan metadata:** (SUMMARY committed by Wave 2 executor)

## Backend Files Created

- `backend-spring/.../follow/dto/FollowStatusDTO.java` — DTO with `isFollowing: boolean`
- `backend-spring/.../follow/controller/FollowController.java` — Added `GET /{id}/follow/status`
- `backend-spring/.../follow/service/FollowService.java` — Added `isFollowing()` interface
- `backend-spring/.../follow/service/impl/FollowServiceImpl.java` — Implemented via `followMapper.exists()`
- `backend-spring/.../user/controller/UserController.java` — Added `GET /by-username/{username}/profile`
- `backend-spring/.../user/service/UserService.java` — Added `getUserProfileByUsername()` interface
- `backend-spring/.../user/service/impl/UserServiceImpl.java` — Implemented via `findByUsername` + `getUserProfile`
- `backend-spring/.../achievement/controller/AchievementController.java` — Added `GET /user/{id}` for any user

## Decisions Made

- Follow status uses existing `FollowMapper.exists()` — no new mapper method needed
- Username resolution composes existing `findByUsername` with `getUserProfile` — no code duplication
- Achievement endpoint placed before `/user/me` in AchievementController to prevent Spring path matching conflict

## Deviations from Plan

None — plan executed as written. Note: frontend artifacts (followApi, useFollowStatus, i18n, route) were created by Wave 2 (29-02) since both waves ran concurrently and Wave 1's frontend task was not committed.

## Issues Encountered

- Wave 1 executor only committed backend files — frontend API/composable/route/i18n created by Wave 2
- Maven compile verification was deferred to post-merge test gate

## Next Phase Readiness

- Backend endpoints verified by Wave 2 ProfileView.vue which imports and uses followApi and fetchProfileByUsername
- Frontend API layer and composable ready for FollowButton and ProfileView components

---
*Phase: 29-01*
*Completed: 2026-04-21*
