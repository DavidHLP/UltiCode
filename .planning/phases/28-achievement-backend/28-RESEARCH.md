# Phase 28: Achievement Backend - Research

**Date:** 2026-04-21
**Goal:** Achievement system is complete with triggers, progress tracking, categories, and real-time notifications

## Existing Infrastructure

### Already In Place

- `@EnableAsync` configured in `UlticodeBackendApplication.java`
- `AchievementTriggerServiceImpl` with 10 trigger methods
- `AchievementEarnedEvent` record published via `ApplicationEventPublisher`
- `NotificationService.sendBadgeEarned(userId, payload)` — WebSocket dispatch ready
- `BadgeEarnedPayload` DTO for WebSocket notification
- WebSocket via `SimpMessagingTemplate` → `/user/{userId}/queue/notifications`
- `AchievementType` enum with 11 types

### AchievementType Enum (current)

```java
PROBLEMS_SOLVED("problems_solved"),
SUBMISSIONS_MADE("submissions_made"),
CONTEST_PARTICIPATION("contest_participation"),
CONTEST_WINS("contest_wins"),
CONTEST_PLACED("contest_placed"),
FORUM_POSTS("forum_posts"),
SOLUTIONS_WRITTEN("solutions_written"),
STREAK_DAYS("streak_days"),
RATING_MILESTONE("rating_milestone"),
COMMUNITY_CONTRIBUTOR("community_contributor"),
FOLLOWER_COUNT("follower_count");
```

## Gaps Identified

### Gap 1: Missing Async Event Listener

**Problem:** `AchievementEarnedEvent` is published via `ApplicationEventPublisher` in `AchievementTriggerServiceImpl.checkAndAwardAchievements()`, but no `@Async` `@EventListener` consumes it to push the WebSocket notification.

**Solution:** Create a new `@Service` class (e.g., `AchievementNotificationListener`) with an `@Async` `@EventListener` method that receives `AchievementEarnedEvent` and calls `NotificationService.sendBadgeEarned(userId, payload)`.

```java
@Async
@EventListener
public void onAchievementEarned(AchievementEarnedEvent event) {
    BadgeEarnedPayload payload = BadgeEarnedPayload.of(...);
    notificationService.sendBadgeEarned(event.userId(), payload);
}
```

### Gap 2: Missing Achievement Types

**Missing types needed:**
- `FIRST_PROBLEM` — triggered on first AC problem solved
- `LANGUAGE_SOLVED` — per-language milestones (1/10/50/100 problems in a language)

**Changes required:**
1. Add `FIRST_PROBLEM("first_problem")` and `LANGUAGE_SOLVED("language_solved")` to `AchievementType` enum
2. Add `onFirstProblemSolved(String userId)` trigger method to `AchievementTriggerService`
3. Add `onLanguageMilestone(String userId, String language, int count)` trigger method
4. Wire `onFirstProblemSolved()` in `SubmissionServiceImpl` after successful judging (when result is AC)

### Gap 3: Progress Endpoint Not Implemented

**Problem:** `GET /users/me/achievements/progress` does not exist. Only `GET /achievements/user/me` (returns earned achievements) exists.

**Solution:** Add `GET /users/me/achievements/progress` endpoint in `UserController` (following the pattern of Phase 27's `/users/{id}/profile`):

1. Create `AchievementProgressVO` with: achievementId, key, name, icon, tier, category, currentValue, targetValue, percentage, nextMilestone
2. `AchievementService.getUserProgress(userId)` — for each unearned achievement, calculate progress from:
   - Current count from stats mappers (SubmissionMapper, FollowMapper, etc.)
   - Target from achievement criteria
   - Percentage = currentValue / targetValue * 100
   - Next milestone target

### Gap 4: Category Filtering Unvalidated

**Problem:** `AchievementController.list()` accepts `category` param but no validation exists.

**Solution:** Add validation against known categories in `AchievementServiceImpl.list()` or `AchievementController`:

**Known categories:** `problems`, `contests`, `social`, `streaks`, `special`

Return 400 Bad Request if unknown category provided.

## Integration Points

| Trigger | Where to wire | Current state |
|---------|---------------|---------------|
| `onFollowCountUpdated()` | `FollowServiceImpl` | Already wired with @Async |
| `onContestJoined()` | `ContestService` | Not yet wired |
| `onContestWon()` | `ContestService` | Not yet wired |
| `onFirstProblemSolved()` | `SubmissionServiceImpl` | New — needs wiring |
| `onLanguageMilestone()` | `SubmissionServiceImpl` | New — needs wiring |

## Verification Criteria

1. `GET /achievements?category=problems` returns only problem achievements
2. `GET /achievements?category=invalid` returns 400
3. `GET /users/me/achievements/progress` returns unearned achievements with progress fields
4. Earning first achievement triggers WebSocket notification via async event listener
5. New `AchievementType` values (`FIRST_PROBLEM`, `LANGUAGE_SOLVED`) exist in enum
6. Language milestone trigger correctly aggregates per-language submission counts

---
*Research: Phase 28 - Achievement Backend*
