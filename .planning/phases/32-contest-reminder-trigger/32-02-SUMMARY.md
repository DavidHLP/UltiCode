---
phase: "32-contest-reminder-trigger"
plan: "02"
subsystem: notification
tags: [gap-closure, notification, metadata, persistence]
dependency_graph:
  requires: []
  provides:
    - id: "NOTIF-04"
      description: "Contest Reminder Trigger uses metadata Map for deduplication and contest details"
  affects:
    - "NotificationService.createNotification()"
    - "ContestScheduler.sendContestReminders()"
tech_stack:
  added:
    - "Map<String, Object> metadata parameter on NotificationService interface"
  patterns:
    - "Metadata Map stored via JacksonTypeHandler on Notification entity"
    - "setMetadata() called before insert for persistence"
    - "WebSocket NotificationPayload gets real metadata instead of null"
key_files:
  created: []
  modified:
    - "backend-spring/src/main/java/com/ulticode/modules/notification/service/NotificationService.java"
    - "backend-spring/src/main/java/com/ulticode/modules/notification/service/impl/NotificationServiceImpl.java"
    - "backend-spring/src/main/java/com/ulticode/modules/contest/scheduler/ContestScheduler.java"
    - "backend-spring/src/main/java/com/ulticode/modules/follow/service/impl/FollowServiceImpl.java"
    - "backend-spring/src/main/java/com/ulticode/modules/achievement/listener/AchievementNotificationListener.java"
decisions:
  - id: "DECISION-01"
    decision: "Pass null for metadata in existing callers (FollowServiceImpl, AchievementNotificationListener) that do not need structured metadata"
    rationale: "These notifications have no additional structured data to store; passing null is a no-op that preserves existing behavior while satisfying the new interface signature"
decisions_made:
  - "Existing callers that do not use metadata pass null to satisfy the new interface signature"
metrics:
  duration: "~3 minutes"
  completed: "2026-04-21T12:58:00Z"
---

# Phase 32 Plan 02: Contest Reminder Trigger - Metadata Persistence Gap Closure

## One-liner

Add `Map<String, Object> metadata` parameter to `NotificationService.createNotification()` so contest reminder metadata (contestId, contestTitle, startTime, dedupKey) persists to the database and flows through WebSocket push.

## What Was Done

### Tasks Completed

| # | Task | Commit | Status |
|---|------|--------|--------|
| 1 | Add metadata parameter to NotificationService interface | `6d6fad4ea` | Done |
| 2 | Implement metadata persistence in NotificationServiceImpl + fix callers | `7b60cf014` | Done |

### Task 1: Add metadata parameter to NotificationService interface

- Added `Map<String, Object> metadata` as the 7th parameter to `createNotification()` in the interface
- Updated Javadoc to document the metadata parameter
- File: `NotificationService.java`

### Task 2: Implement metadata persistence + fix all callers

- **NotificationServiceImpl**: Updated method signature to accept `Map<String, Object> metadata` and added `notification.setMetadata(metadata)` before `notificationMapper.insert(notification)`. This ensures metadata is persisted to the `notifications` table via the existing `JacksonTypeHandler`
- **ContestScheduler**: Updated the `createNotification()` call in `sendContestReminder()` to pass the `metadata` map (containing `contestId`, `contestTitle`, `startTime`, `dedupKey`) as the last argument
- **FollowServiceImpl**: Fixed caller to pass `null` for metadata (existing behavior preserved, no structured data needed)
- **AchievementNotificationListener**: Fixed caller to pass `null` for metadata (existing behavior preserved, no structured data needed)
- Verified: `./mvnw compile -q` succeeds with no errors

## Verification

| Check | Result |
|-------|--------|
| Interface has `Map<String, Object> metadata` in signature | PASS (`grep -n "Map.*metadata"` on NotificationService.java) |
| Implementation calls `notification.setMetadata(metadata)` before insert | PASS (`grep -n "setMetadata"` on NotificationServiceImpl.java) |
| ContestScheduler passes metadata as last arg | PASS (`grep -A5 "createNotification"` on ContestScheduler.java) |
| Compilation succeeds | PASS (`./mvnw compile -q` exits 0) |

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Fixed two additional callers missing metadata parameter**
- **Found during:** Compilation after implementing interface change
- **Issue:** `FollowServiceImpl.java:55` and `AchievementNotificationListener.java:38` also call `createNotification()` with the old 6-parameter signature. Maven compilation failed with "cannot be applied to given types" errors
- **Fix:** Updated both callers to pass `null` as the metadata argument, preserving existing behavior while satisfying the new 7-parameter interface
- **Files modified:** `FollowServiceImpl.java`, `AchievementNotificationListener.java`
- **Commit:** `7b60cf014`

## Threat Flags

| Flag | File | Description |
|------|------|-------------|
| None | - | No new threat surface introduced; this is a gap closure that wires up existing functionality |

## Self-Check: PASSED

- `backend-spring/src/main/java/com/ulticode/modules/notification/service/NotificationService.java` - FOUND (commit 6d6fad4ea)
- `backend-spring/src/main/java/com/ulticode/modules/notification/service/impl/NotificationServiceImpl.java` - FOUND (commit 7b60cf014)
- `backend-spring/src/main/java/com/ulticode/modules/contest/scheduler/ContestScheduler.java` - FOUND (commit 7b60cf014)
- Commits `6d6fad4ea` and `7b60cf014` exist in git history
