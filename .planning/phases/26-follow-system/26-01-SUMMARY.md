---
phase: "26"
plan: "01"
subsystem: follow
tags:
  - follow-system
  - social
  - backend
dependency_graph:
  requires: []
  provides:
    - FollowService
    - FollowController
    - UserFollow entity
  affects:
    - Phase 27 (Profile)
    - Phase 28 (Achievement)
tech_stack:
  added:
    - MyBatis-Plus composite-key entity
    - @Async achievement triggers
    - Spring @EnableAsync
key_files:
  created:
    - backend-spring/src/main/java/com/ulticode/modules/follow/entity/UserFollow.java
    - backend-spring/src/main/java/com/ulticode/modules/follow/mapper/FollowMapper.java
    - backend-spring/src/main/java/com/ulticode/modules/follow/service/FollowService.java
    - backend-spring/src/main/java/com/ulticode/modules/follow/service/impl/FollowServiceImpl.java
    - backend-spring/src/main/java/com/ulticode/modules/follow/controller/FollowController.java
    - backend-spring/src/main/java/com/ulticode/modules/follow/dto/FollowStatsDTO.java
    - backend-spring/src/main/java/com/ulticode/modules/follow/dto/UserSummaryDTO.java
  modified:
    - backend-spring/src/main/java/com/ulticode/modules/achievement/constants/AchievementType.java
    - backend-spring/src/main/java/com/ulticode/modules/achievement/service/AchievementTriggerService.java
    - backend-spring/src/main/java/com/ulticode/modules/achievement/service/impl/AchievementTriggerServiceImpl.java
    - backend-spring/src/main/java/com/ulticode/UlticodeBackendApplication.java
decisions:
  - "Follow relationship stored in user_follows table with composite key (follower_id, following_id)"
  - "Idempotent follow via ON DUPLICATE KEY UPDATE — duplicate follows return success"
  - "@Async on triggerFollowerAchievement() prevents blocking the follow response"
  - "AchievementType.FOLLOWER_COUNT added for follower milestone achievements"
  - "@EnableAsync added to UlticodeBackendApplication for async achievement triggers"
metrics:
  duration: "< 5 min"
  completed: "2026-04-21"
  tasks: "3/3"
  files: "11 created, 3 modified"
---

# Phase 26 Plan 01: Follow System Summary

## One-liner

Complete follow system backend with idempotent follow/unfollow, paginated follower/following lists, and async achievement triggers.

## Completed Tasks

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | UserFollow entity and FollowMapper | 9079441bb | UserFollow.java, FollowMapper.java |
| 2 | FollowService with achievement integration | 28fe2cb13 | FollowService.java, FollowServiceImpl.java, DTOs, AchievementType.java, AchievementTriggerService.java, UlticodeBackendApplication.java |
| 3 | FollowController REST endpoints | 50da5c05c | FollowController.java |

## What Was Built

- **UserFollow entity** — composite key (followerId, followingId, createdAt), no @TableLogic
- **FollowMapper** — custom queries: countByFollowerId, countByFollowingId, exists, insertIdempotent (ON DUPLICATE KEY UPDATE), deleteRelation, paginated selects with OFFSET/LIMIT
- **FollowService interface** — 5 methods: follow, unfollow, getFollowers, getFollowing, getFollowStats
- **FollowServiceImpl** — idempotent follow, self-follow prevention, batch user fetch for list endpoints, @Async achievement triggers
- **FollowController** — POST/DELETE /users/{id}/follow, GET /users/{id}/followers, GET /users/{id}/following
- **FollowStatsDTO** — followerCount, followingCount
- **UserSummaryDTO** — id, username, avatar, bio (max 100 chars), followerCount, followingCount
- **AchievementType.FOLLOWER_COUNT** — added for follower milestone achievements
- **AchievementTriggerService.onFollowCountUpdated()** — new trigger method
- **@EnableAsync** — added to UlticodeBackendApplication

## Deviations from Plan

None — plan executed exactly as written.

## Threats Mitigated

| Threat | Mitigation | Status |
|--------|------------|--------|
| T-26-01 (Tampering) | Path param validation via SecurityUtil.getCurrentUserId() | OK |
| T-26-02 (Repudiation) | Log follow/unfollow actions in FollowServiceImpl | OK |
| T-26-03 (Info Disclosure) | Only public fields in UserSummaryDTO (no email) | OK |
| T-26-04 (DoS) | pageSize capped at 100 in service layer | OK |

## Verification

- Compile check passed (`./mvnw compile -q`)
- All 4 REST endpoints defined
- Self-follow throws BusinessException(FORBIDDEN)
- Duplicate follow is idempotent (ON DUPLICATE KEY UPDATE)
- Achievement triggers called via @Async

## Self-Check: PASSED

All created files exist, all commit hashes verified in git log.

## Requirements Covered

| Requirement | Status |
|-------------|--------|
| FOLLOW-01 (Follow endpoint) | Done |
| FOLLOW-02 (Unfollow endpoint) | Done |
| FOLLOW-04 (Paginated lists) | Done |
