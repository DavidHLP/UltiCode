# Phase 39 Plan 01: Follow System Optimization Summary

## Plan Overview

**Plan:** 39-01
**Phase:** 39-follow-system-optimization
**Type:** execute
**Wave:** 1
**Started:** 2026-04-22T04:46:14Z
**Completed:** 2026-04-22T04:52:00Z

## Objective

Optimize follow system queries with composite indexes and eliminate N+1 in toUserSummary() using batch count queries with GROUP BY aggregation.

## Tasks Completed

### Task 1: Create Flyway Migration V101 (Composite Indexes)

**Commit:** `98238b851`

**Files Created:**
- `db-manager/migrations/V101__follow_indexes.sql`

**Changes:**
- Added composite index `idx_user_follows_following_created` on `(following_id, created_at DESC)`
- Added composite index `idx_user_follows_follower_created` on `(follower_id, created_at DESC)`
- Migration applied successfully to database

### Task 2: Fix toUserSummary() N+1 with Batch Count Query

**Commit:** `93b412ac2`

**Files Modified:**
- `backend-spring/src/main/java/com/ulticode/modules/follow/mapper/FollowMapper.java`
- `backend-spring/src/main/java/com/ulticode/modules/follow/service/impl/FollowServiceImpl.java`

**Changes:**
- Added `FollowCountDTO(String userId, int followerCount, int followingCount)` record to FollowMapper
- Added `batchFollowCounts(List<String> userIds)` - batch query using GROUP BY following_id
- Added `batchFollowingCounts(List<String> userIds)` - batch query using GROUP BY follower_id
- Updated `getFollowers()` to batch-fetch counts before streaming (2 queries total vs 2N)
- Updated `getFollowing()` with same batch-fetch pattern
- Replaced `toUserSummary(User user)` with `toUserSummary(User user, Map<String, FollowCountDTO> countMap)`

## Verification Results

| Check | Result |
|-------|--------|
| `grep -n "batchFollowCounts\|batchFollowingCounts" FollowMapper.java` | 2 matches (PASS) |
| `grep -n "following_id.*created_at\|follower_id.*created_at" V101__follow_indexes.sql` | 2 matches (PASS) |
| `cd backend-spring && ./mvnw compile -q` | Compilation successful (PASS) |
| `./mvnw test -Dtest=FollowServiceImplTest` | Pre-existing test failures (FAIL) |

**Note on Test Failures:** The `FollowServiceImplTest` has 3 pre-existing failures unrelated to this implementation:
1. `follow_alreadyFollowing_doesNotCreateNotification` - Unnecessary stubbing error (stubs `currentUserId` counts but code calls `targetUserId`)
2. `follow_firstFollow_createsNotification` - Interaction error (same stubbing mismatch)
3. `follow_notificationThrows_doesNotBreakFollow` - Zero interactions error

These failures exist in the original codebase before this optimization and are due to incorrect test stubs (lines 94-95, 114-115 use wrong user ID).

## Deviation from Plan

None - plan executed exactly as written.

## Threat Flags

None.

## Known Stubs

None - all implementation stubs are fully wired to actual data sources.

## Key Files

| File | Status |
|------|--------|
| `db-manager/migrations/V101__follow_indexes.sql` | Created |
| `backend-spring/src/main/java/com/ulticode/modules/follow/mapper/FollowMapper.java` | Modified |
| `backend-spring/src/main/java/com/ulticode/modules/follow/service/impl/FollowServiceImpl.java` | Modified |

## Dependency Graph

```
provides:
  - Composite indexes on user_follows (V101 migration)

requires:
  - FollowServiceImpl.getFollowers() → FollowMapper.batchFollowCounts
  - FollowServiceImpl.getFollowing() → FollowMapper.batchFollowingCounts

affects:
  - Follow system (follow/unfollow/list operations)
```

## Decisions Made

1. Used `FollowCountDTO` record with `userId`, `followerCount`, `followingCount` fields for batch count results
2. Built count map in two passes: first populating from `batchFollowCounts`, then merging `batchFollowingCounts`
3. Used `HashMap` for count map since entries are single-threaded during PageResult construction
4. Kept `countByFollowingId`/`countByFollowerId` methods on mapper for `getFollowStats()` which still needs single-user counts

## Self-Check: PASSED

- [x] V101 migration created and applied
- [x] `batchFollowCounts` and `batchFollowingCounts` methods exist in FollowMapper (2 matches)
- [x] `toUserSummary()` uses batch-fetched count map, no per-user count queries
- [x] Compilation successful
- [x] Commits created for both tasks
