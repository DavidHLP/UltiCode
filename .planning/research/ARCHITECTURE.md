# Architecture Patterns — v1.6 User & Social

**Domain:** Online programming platform social features
**Researched:** 2026-04-21
**Confidence:** MEDIUM-HIGH

## Executive Summary

The v1.6 User & Social milestone adds three interrelated features to an existing Spring Boot + Vue 3 platform: enhanced user profiles, an achievement/badge system, and a follow system. The achievement system is substantially complete (backend exists, migration V22 done). The follow system is net-new. User profiles need targeted enhancements to existing UserVO and UserController.

## Existing Architecture

### Current User Module (`modules/user/`)

```
UserController
    GET  /users/me              → getCurrentUser()
    PATCH /users/me             → updateCurrentUser()
    GET  /users                 → listUsers(page, pageSize)
    GET  /users/{id}            → getUserById()
    GET  /users/{id}/stats      → getUserStatsById()
    GET  /users/{id}/skills     → getUserSkillsById()

UserService (interface)
    findById(id), findByUsername(username), findByEmail(email)
    getCurrentUser(), updateCurrentUser(), listUsers()
    getUserById(), getUserStatsById(), getUserSkillsById()
```

**Existing User Entity fields:** id, username, name, email, avatar, password, bio, company, github, joinedAt, location, twitter, website, preferredLanguage, role, isActive, isBanned, bannedUntil, bannedReason, lastLoginAt, createdBy, updatedBy, isDeleted, deletedAt, deletedBy

### Existing Achievement Module (`modules/achievement/`)

```
AchievementTriggerServiceImpl
    onProblemSolved(userId, count)       → checks PROBLEMS_SOLVED achievements
    onSubmissionMade(userId, count)      → checks SUBMISSIONS_MADE achievements
    onContestJoined(userId, count)      → checks CONTEST_PARTICIPATION achievements
    onContestWon(userId, count)         → checks CONTEST_WINS achievements
    onContestPlaced(userId, count)      → checks CONTEST_PLACED achievements
    onForumPostCreated(userId, count)    → checks FORUM_POSTS achievements
    onSolutionWritten(userId, count)     → checks SOLUTIONS_WRITTEN achievements
    onStreakUpdated(userId, days)       → checks STREAK_DAYS achievements
    onRatingUpdated(userId, rating)      → checks RATING_MILESTONE achievements

Integration: AchievementEarnedEvent published → RealtimeService → WebSocket → BadgeEarnedPayload
```

### Existing Frontend API Layer (`console/src/api/`)

- `achievement.ts` — getAll(), getById(), getUserAchievements(), getUserPoints()
- `user.ts` — fetchUserProfile(), updateUserProfile(), fetchUserStats(), fetchUserSkills()

---

## Recommended Architecture

### New Component: Follow System

```
modules/follow/
    entity/
        UserFollow.java          # Table: user_follows
    mapper/
        UserFollowMapper.java
    service/
        FollowService.java       # Interface
        impl/FollowServiceImpl.java
    controller/
        FollowController.java    # REST endpoints
    dto/
        FollowStatusVO.java      # { isFollowing, followerCount, followingCount }
```

**Database table:**
```sql
CREATE TABLE user_follows (
    id          VARCHAR(40) PRIMARY KEY,
    follower_id  VARCHAR(40) NOT NULL,  -- the user who follows
    following_id VARCHAR(40) NOT NULL,  -- the user being followed
    created_at   DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY user_follows_follower_following_key (follower_id, following_id),
    KEY user_follows_following_id_idx (following_id),
    FOREIGN KEY (follower_id) REFERENCES users(id),
    FOREIGN KEY (following_id) REFERENCES users(id)
);
```

**FollowController endpoints:**
```
GET  /users/{id}/follow-status      → FollowStatusVO (isFollowing, counts)
POST /users/{id}/follow             → follow a user
DELETE /users/{id}/follow           → unfollow a user
GET  /users/{id}/followers          → paginated follower list
GET  /users/{id}/following         → paginated following list
```

### Integration: Follow → Achievement Trigger

Add to `AchievementTriggerServiceImpl`:
```java
public List<String> onFollowCountUpdated(String userId, int followerCount) {
    return checkAndAwardAchievements(userId, AchievementType.FOLLOWER_MILESTONE, followerCount);
}
```

Call from `FollowServiceImpl` after each follow/unfollow operation.

---

## User Profile Enhancements

### Current Gap: Missing Social Stats in UserVO

Existing `UserVO` maps User entity fields. The profile page also needs:
- `rank` — user's global ranking
- `solvedCount` — total problems solved
- `submissionCount` — total submissions
- `followerCount` — followers (from new follow system)
- `followingCount` — following (from new follow system)

**Approach:** Create a new `UserProfileVO` that extends/nests `UserVO` with social stats, or add these as additional fields populated via JOIN or subsequent queries.

```java
public record UserProfileVO(
    UserVO user,
    Integer rank,
    Integer solvedCount,
    Integer submissionCount,
    Integer followerCount,
    Integer followingCount
) {}
```

### UserController Enhancement

```
GET /users/{id}/profile  → UserProfileVO (full profile with social stats)
```

---

## Data Flow Diagrams

### Follow Flow
```
User A                    API                      FollowService           MyBatis
  |---POST /users/B/follow--->|-------------------------->|------------------->|
                            |   validate B exists        |                     |
                            |   check not self-follow    |                     |
                            |   check not already following|                   |
                            |-------------------------->|insert into user_follows|
                            |   incrementFollowerCount(B)|                    |
                            |<--return FollowStatusVO----|                     |
                            |<-----------------------------------------------|
```

### Achievement Trigger Flow (existing)
```
SubmissionService         AchievementTriggerService    AchievementEarnedEvent    RealtimeService    WebSocket
    |--onSubmissionMade()->|---------------------------->|------------------------>|----------------->|
                          |   check thresholds          |                         |                 |
                          |   award new achievements   |publish event             |                 |
                          |<----------------------------|                         |                 |
                          |                             |------BadgeEarnedPayload->|--> browser ---->|
```

---

## Component Boundaries

| Component | Responsibility | Communicates With |
|-----------|---------------|-------------------|
| `modules/follow/` | Follow relationships, counts | UserService (user validation), AchievementTriggerService (milestone triggers) |
| `modules/achievement/` | Achievement definitions, triggers, earned records | All services that trigger onX() events, RealtimeService for WebSocket |
| `modules/user/` | User CRUD, profiles, stats | FollowService (for profile VO), AchievementService (for user points) |
| `modules/websocket/` | Realtime push | Achievement module publishes events |

---

## Build Order

1. **Follow system** — new tables, new module, controller, service
   - `V26__follow_schema.sql` (migration)
   - `modules/follow/entity/UserFollow.java`
   - `modules/follow/mapper/UserFollowMapper.java`
   - `modules/follow/service/FollowService.java` + `impl/`
   - `modules/follow/controller/FollowController.java`
   - Add `onFollowCountUpdated()` to `AchievementTriggerService`
   - Unit tests for FollowService

2. **User profile enhancements** — modify existing, no new module
   - Create `UserProfileVO` record
   - Add `GET /users/{id}/profile` endpoint returning full profile
   - Update `UserVO` if needed for frontend compatibility
   - Unit tests for new endpoint

3. **Achievement completion** — verify existing, wire missing triggers
   - Verify V22 migration is applied and complete
   - Confirm all `onX()` methods are called from appropriate services (submission, contest, forum, solution)
   - Add follow milestone to `AchievementType` enum
   - Add `onFollowCountUpdated()` call from FollowService
   - WebSocket notification already wired via `BadgeEarnedPayload`

4. **Frontend integration** — API clients and UI
   - Extend `user.ts` with follow-related calls
   - Create `follow.ts` API client
   - Profile page component with follow button
   - Achievements display component
   - Followers/following list components

---

## Anti-Patterns to Avoid

### Do not poll achievement counts
Use event-driven updates (AchievementEarnedEvent) rather than scheduled polling.

### Do not duplicate follow counts on User entity
Store follow counts computed via `SELECT COUNT(*)` or maintained via triggers, not stored redundant columns on users table.

### Do not expose private user fields in UserVO
Ensure UserVO never leaks password, email (unless own profile), or banned fields to public endpoints.

---

## Scalability Considerations

| Concern | Approach |
|---------|----------|
| Follower list pagination | Cursor-based or OFFSET with indexed `following_id` |
| Achievement trigger performance | Batch check on first access of day, not per-submission |
| Real-time badge notification | WebSocket already in place via RealtimeService |
| Profile page load | Cache UserProfileVO in Redis (already have caching layer from v1.5) |

---

## Sources

- Existing codebase: `backend-spring/src/main/java/com/ulticode/modules/user/`, `modules/achievement/`
- Existing migrations: `V1__core_schema.sql`, `V22__achievement_schema.sql`
- Existing frontend: `console/src/api/user.ts`, `console/src/api/achievement.ts`
- Project context: `.planning/PROJECT.md` v1.6 User & Social milestone
