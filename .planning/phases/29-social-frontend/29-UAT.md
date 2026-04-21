---
status: complete
phase: 29-social-frontend
source: [29-01-SUMMARY.md, 29-02-SUMMARY.md]
started: 2026-04-21T00:00:00Z
updated: 2026-04-21T00:00:00Z
---

## Current Test

[testing complete]

## Tests

### 1. Cold Start Smoke Test
expected: Backend and frontend start without errors. MySQL and Redis are reachable.
  Health endpoint returns 200. No Flyway migration failures.
result: pass

### 2. Follow Status API
expected: |
  GET /users/{id}/follow/status returns {"isFollowing": boolean}.
  Returns true when current user follows target user, false otherwise.
  Requires authentication (session cookie).
result: pass

### 3. Username-to-Profile Lookup
expected: |
  GET /users/by-username/{username}/profile returns full user profile.
  Includes username, display name, bio, avatar, and stats.
result: pass

### 4. Any-User Achievement API
expected: |
  GET /achievements/user/{id} returns list of achievements for any user.
  Public endpoint — does not require viewing own profile.
result: pass

### 5. Follow/Unfollow Button States
expected: |
  FollowButton renders three states correctly:
  - "Follow" (outline) when not following
  - "Following" (default) when following
  - "Unfollow" (destructive) on hover
  Button is hidden when viewing own profile.
result: pass

### 6. Profile Page Load
expected: |
  GET /profile/:username loads ProfileView with:
  - Avatar, username, name, bio
  - 6 StatsCards: problems solved, global rank, acceptance rate,
    total submissions, followers, following
  - Top 5 earned achievements
result: pass

### 7. Optimistic Follow Toggle
expected: |
  Clicking Follow immediately updates UI to "Following" state.
  On API failure, UI rolls back to previous state and shows error.
result: pass

### 8. i18n Keys Present
expected: |
  Both en-US and zh-CN personal.ts contain all required social keys:
  problemsSolvedSubtitle, globalRankSubtitle, followersSubtitle,
  followingSubtitle, noAchievements, retry, totalSubmissions, submissionsSubtitle
result: pass

## Summary

total: 8
passed: 8
issues: 0
pending: 0
skipped: 0

## Gaps

- truth: "user_follows table should exist"
  status: identified_fixed
  reason: "Missing Flyway migration V26__follow_schema.sql caused all follow endpoints to return 50000. Created migration and applied directly via MySQL client."
  severity: blocker
  test: 2
  artifacts: [db-manager/migrations/V26__follow_schema.sql]
  missing: []

## Resolved Issues

**Issue: Missing user_follows migration (BLOCKER)**
- Identified during UAT — all follow endpoints returned 50000 Unknown error
- Root cause: `UserFollow` entity mapped to `user_follows` table but no Flyway migration existed
- Fix: Created `V26__follow_schema.sql` and applied via MySQL client
- Verified: All 3 follow endpoints now return 200 with correct data
