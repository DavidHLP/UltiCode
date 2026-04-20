# UltiCode — v1.6 Requirements

**Milestone:** v1.6 User & Social
**Date:** 2026-04-21
**Status:** Ready for Planning

## Traceability

| REQ-ID | Requirement | Phase | Status |
|--------|-------------|-------|--------|
| PROF-01 | User Profile Endpoint | Phase 27 | Pending |
| PROF-02 | Profile Page Frontend | Phase 29 | Pending |
| PROF-03 | Profile Edit | Phase 27 | Pending |
| ACHV-01 | Achievement Trigger Completion | Phase 28 | Pending |
| ACHV-02 | Achievement Progress Indicators | Phase 28 | Pending |
| ACHV-03 | Achievement Categories and Filtering | Phase 28 | Pending |
| ACHV-04 | Real-time Achievement Notification | Phase 28 | Pending |
| FOLLOW-01 | Follow/Unfollow | Phase 26 | Pending |
| FOLLOW-02 | Follower/Following Lists | Phase 26 | Pending |
| FOLLOW-03 | Follow Button State | Phase 29 | Pending |
| FOLLOW-04 | Achievement Integration | Phase 26 | Pending |

---

## User Profile

### PROFILE-01: User Profile Endpoint
**User can view any user's public profile page** via `GET /users/{id}/profile` returning a `UserProfileVO` that includes:
- Basic info: username, avatar, bio, company, github, twitter, location, join date
- Social stats: follower count, following count
- Activity stats: problems solved count, submissions count, contest rating
- Recent achievements (top 5 earned badges)

**Notes from research:** User entity already has all basic fields. Need to design UserProfileVO to aggregate stats. N+1 risk on achievements — use JOIN FETCH.

### PROFILE-02: Profile Page Frontend
**User can view a user's public profile page** in the Console frontend at `/profile/{username}` with:
- Profile header: avatar, username, bio, links (github/twitter), join date
- Stats cards: problems solved, submissions, contest rating, follower/following counts
- Achievements section: earned badges displayed as cards
- Follow button (visible only when viewing other users' profiles)

### PROFILE-03: Profile Edit
**User can edit their own profile** via `PUT /users/me/profile` including:
- Bio (max 500 characters)
- Company, location, website
- Avatar upload (MultipartFile, stored locally or to object storage path)

---

## Achievement System

### ACHV-01: Achievement Trigger Completion
**System awards achievements automatically** when users trigger achievement criteria:
- First problem solved (first-ac)
- Language milestones (solve 1/10/50/100 problems in a language)
- Follower milestones (reach 10/50/100 followers)
- Streak achievements (consecutive days active)
- Contest achievements (participate/win first contest)

**Notes from research:** `AchievementTriggerServiceImpl` exists with most triggers. Missing: follow count, language milestones, first-problem. Must use `@Async` event listener to avoid blocking.

### ACHV-02: Achievement Progress Indicators
**User can view progress toward achievements they have not yet earned** via `GET /users/me/achievements/progress` returning for each unearned achievement:
- Current progress (e.g., "7/10 problems in Python")
- Percentage complete
- Next milestone target

### ACHV-03: Achievement Categories and Filtering
**User can browse achievements by category** via `GET /achievements?category={category}` with categories:
- Problems (solving milestones)
- Contests (participation/winning)
- Social (followers, following)
- Streaks (consecutive activity)
- Special (rare achievements)

### ACHV-04: Real-time Achievement Notification
**User receives a WebSocket notification** immediately when they earn an achievement, displaying:
- Achievement name and description
- Badge icon/image
- Rarity tier (common/uncommon/rare/epic/legendary)

**Notes from research:** `BadgeEarnedPayload` WebSocket payload exists. Must verify `@Async` event wiring is working.

---

## Follow System

### FOLLOW-01: Follow/Unfollow
**User can follow another user** via `POST /users/{id}/follow` and **unfollow** via `DELETE /users/{id}/follow`.
- Users cannot follow themselves
- Duplicate follows are idempotent (no error)
- Returns updated follower/following counts

### FOLLOW-02: Follower/Following Lists
**User can view who they follow and who follows them** via:
- `GET /users/{id}/following` — list of users this user follows (paginated)
- `GET /users/{id}/followers` — list of users who follow this user (paginated)
- Response includes username, avatar, bio snippet for each user

**Notes from research:** Must use cursor-based pagination for large datasets. Follow table needs composite indexes on `(follower_id, following_id)` and `(following_id, follower_id)` to prevent timeouts on popular users.

### FOLLOW-03: Follow Button State
**Frontend follow button reflects real-time state** showing:
- "Follow" if not currently following
- "Following" if currently following (with hover to reveal "Unfollow")

### FOLLOW-04: Achievement Integration
**Earning achievements triggers when follow milestones are reached** — `onFollowCountUpdated()` is called in `FollowServiceImpl` after each follow/unfollow to check and award FOLLOWER_MILESTONE achievements asynchronously.

---

## Future Requirements (Deferred)

- Activity feed / follow timeline — high complexity, deferred to v1.7
- Suggested users to follow — needs recommendation service integration
- Social sharing of achievements — share link generation
- Profile views counter — marginal value
- Follow notifications (email/in-app) for new followers

---

## Out of Scope

| Requirement | Reason |
|-------------|--------|
| Activity feed | v2+ — requires new aggregation paradigm |
| Direct messaging | Not a core platform feature for v1.6 |
| User blocking/muting | Can be added post-launch |
| Profile customization themes | Nice-to-have, not table stakes |
| Public/Private profile toggle | MVP scope |

---

## Notes

- **Async achievement triggering:** Must verify `@EnableAsync` is configured in Spring Boot before wiring achievement triggers. Existing `ApplicationEventPublisher` pattern must be used for async delivery.
- **Follow table indexes:** Migration must include composite indexes to prevent query timeouts on popular users.
- **Avatar upload:** MVP uses local file storage via MultipartFile. Object storage (S3/OSS) deferred to future.
- **N+1 on achievements:** `UserAchievement` stores only `achievementId` — use JOIN FETCH or batch fetch when loading achievement details for display.
