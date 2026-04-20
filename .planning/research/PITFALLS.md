# Domain Pitfalls: User Profiles, Achievements, and Follow System (v1.6)

**Domain:** Online Programming Platform - Social Features
**Researched:** 2026-04-21
**Confidence:** MEDIUM-HIGH (based on existing achievement module analysis + known social system patterns)

---

## Critical Pitfalls

Mistakes that cause production issues or require significant rework.

---

### Pitfall 1: Synchronous Achievement Triggering Blocks User Actions

**What goes wrong:** Calling `achievementTriggerService.checkAndAwardAchievements()` inside submission/contest handlers makes the user wait for achievement evaluation.

**Why it happens:** The `AchievementTriggerServiceImpl.checkAndAwardAchievements()` runs a full table scan on every problem solve, contest join, or submission:

```java
// Current implementation at line 90:
List<Achievement> allAchievements = achievementMapper.findAllActive(); // Full table scan EVERY event
```

**Consequences:**
- Submission latency spikes on every solve (N achievements checked synchronously)
- User-facing API timeouts when achievement logic is slow
- Compound effect: 1000 users solving problems simultaneously all trigger achievement scans

**Prevention:** Defer achievement checking to async:

```java
// WRONG - blocking the submission response
public void onProblemSolved(String userId, int count) {
    List<String> awarded = triggerService.checkAndAwardAchievements(...); // Blocks here
}

// CORRECT - fire-and-forget event
public void onProblemSolved(String userId, int count) {
    applicationEventPublisher.publishEvent(new ProblemSolvedEvent(userId, count));
}

// Separate listener (async):
@Async
@EventListener
public void handleProblemSolved(ProblemSolvedEvent event) {
    triggerService.checkAndAwardAchievements(...);
}
```

**Phase:** Achievement implementation must be async from day one.

---

### Pitfall 2: N+1 When Loading User Achievement History

**What goes wrong:** Displaying a user's profile with achievements triggers 1 query for user + N queries for each achievement detail.

**Why it happens:** `UserAchievement` stores only `achievementId` (UUID). Displaying achievement name/icon requires joining to `achievements` table per row, or lazy loading in a loop.

**Current schema:**
```sql
-- user_achievements table
user_id | achievement_id | earned_at

-- achievements table
id | key | name | icon | description | ...
```

**Prevention:** Use JOIN FETCH or batch fetch:

```java
// WRONG - N+1
List<UserAchievement> userAchievements = userAchievementMapper.selectByUserId(userId);
userAchievements.forEach(ua -> {
    Achievement detail = achievementMapper.selectById(ua.getAchievementId()); // N queries
});

// CORRECT - single query with JOIN
@Select("SELECT ua.*, a.key, a.name, a.icon, a.description, a.tier, a.points " +
        "FROM user_achievements ua JOIN achievements a ON ua.achievement_id = a.id " +
        "WHERE ua.user_id = #{userId} ORDER BY ua.earned_at DESC")
List<UserAchievementVO> selectUserAchievementsWithDetails(String userId);
```

**Phase:** User Profile display phase.

---

### Pitfall 3: Follow System Missing Index Causes Slow Queries on Popular Users

**What goes wrong:** Querying "who does this popular user follow" or "who follows this popular user" times out when the user has 10K+ followers.

**Why it happens:** No index on `(follower_id, following_id)` or `(following_id, follower_id)` pairs. MySQL does full table scan.

**Consequences:**
- Profile page for popular users (celebrity programmers) loads in 10+ seconds
- Following a popular user takes 5+ seconds
- Unbounded query on `user_follows` table grows to millions of rows

**Prevention:** Add composite indexes before shipping:

```sql
-- In Flyway migration for follow feature
CREATE TABLE user_follows (
    id VARCHAR(36) PRIMARY KEY,
    follower_id VARCHAR(36) NOT NULL,
    following_id VARCHAR(36) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_follow (follower_id, following_id),  -- prevents duplicate follows
    INDEX idx_following (following_id, created_at),       -- "who follows X" ordered by time
    INDEX idx_follower (follower_id, created_at)          -- "who does X follow" ordered by time
);
```

**Phase:** Follow system database migration phase.

---

### Pitfall 4: Fan-Out on Write Explodes for Popular Users

**What goes wrong:** When a popular user posts/solves a problem, sending notifications to all followers takes forever.

**Why it happens:** Naive implementation inserts a notification row per follower:

```java
// WRONG - O(followers) write per action
for (String followerId : followers) {
    notificationService.notify(followerId, event); // 10K writes for celebrity
}
```

**Consequences:**
- Contest announcement to 50K followers times out
- User solves problem -> notification write blocks for 30 seconds
- Database connection pool exhausted during fan-out

**Prevention:** Use async fan-out queue:

```java
// CORRECT - O(1) write, O(followers) async delivery
notificationQueue.publish(new FanOutEvent(userId, "CONTEST_WON", contestId));

// Separate worker processes fan-out
@Async
public void processFanOut(FanOutEvent event) {
    List<String> followers = followMapper.findFollowerIds(event.getUserId());
    for (String followerId : followers) {
        notificationService.sendToUser(followerId, event);
    }
}
```

**Phase:** Follow + Notification integration phase.

---

### Pitfall 5: Achievement Criteria JSON Prevents Database Indexing

**What goes wrong:** Current `Achievement.criteria` is `Map<String, Object>` stored as JSON. Searching for "all achievements of type CONTEST_WINS" requires JSON parsing every row.

**Current schema:**
```java
@TableField(typeHandler = JacksonTypeHandler.class)
private Map<String, Object> criteria;  // {"type": "contest_wins", "target": 10}
```

**Why it happens:** MySQL cannot index JSON columns efficiently for this query pattern.

**Consequences:**
- `findAllActive()` in `checkAndAwardAchievements()` does full table scan every time a user solves a problem
- Adding new achievement types requires application-side filtering
- Cannot efficiently query "how many users earned achievement X"

**Prevention:** Normalize criteria into separate columns:

```sql
ALTER TABLE achievements ADD COLUMN criteria_type VARCHAR(50) NOT NULL;
ALTER TABLE achievements ADD COLUMN criteria_target INT NOT NULL DEFAULT 0;
ALTER TABLE achievements ADD INDEX idx_criteria_type (criteria_type);

-- Keep JSON for additional flexibility, but index the common query fields
```

**Phase:** Achievement data model phase.

---

## Moderate Pitfalls

---

### Pitfall 6: Profile Stats Recomputed on Every Read

**What goes wrong:** User profile page shows submission count, problems solved, contest rating - computed with aggregation queries on every page load.

**Why it happens:** Stats stored in `users` table are not kept in sync, or stats are derived from other tables on every request.

**Prevention:** Maintain denormalized counters:

```sql
-- Add to users table or a user_stats table
total_submissions INT DEFAULT 0,
problems_solved INT DEFAULT 0,
contest_rating INT DEFAULT 0,
follower_count INT DEFAULT 0,
following_count INT DEFAULT 0,
last_updated TIMESTAMP

-- Update atomically when events happen:
UPDATE users SET problems_solved = problems_solved + 1 WHERE id = #{userId}
```

**Phase:** User Profile display phase.

---

### Pitfall 7: Avatar Upload Without Size/Type Validation

**What goes wrong:** Users upload multi-megabyte images or malicious file types as avatars.

**Why it happens:** File validation done client-side only, or server trusts client-reported dimensions.

**Prevention:** Server-side validation:

```java
public String uploadAvatar(MultipartFile file) {
    // Validate size (max 2MB)
    if (file.getSize() > 2 * 1024 * 1024) {
        throw new BadRequestException("Avatar must be under 2MB");
    }
    // Validate content type
    String contentType = file.getContentType();
    if (!Set.of("image/jpeg", "image/png", "image/webp").contains(contentType)) {
        throw new BadRequestException("Avatar must be JPEG, PNG, or WebP");
    }
    // Validate actual image dimensions
    BufferedImage img = ImageIO.read(file.getInputStream());
    if (img.getWidth() > 2000 || img.getHeight() > 2000) {
        throw new BadRequestException("Avatar must be under 2000x2000 pixels");
    }
    // Process and store
}
```

**Phase:** User Profile CRUD phase.

---

### Pitfall 8: Race Condition in Follow/Unfollow

**What goes wrong:** Double-follow or inconsistent state when user spam-clicks follow button.

**Why it happens:** No unique constraint or no optimistic locking.

**Current schema (from user entity):**
```sql
-- If no UNIQUE constraint on (follower_id, following_id):
-- Two rapid clicks -> two follow records inserted
```

**Prevention:** Add unique constraint + idempotent service method:

```java
@Transactional
public void follow(String followerId, String followingId) {
    if (followMapper.exists(followerId, followingId)) {
        return; // Idempotent - already following
    }
    followMapper.insert(followerId, followingId);
    // Also increment denormalized counter
}
```

**Phase:** Follow system implementation phase.

---

### Pitfall 9: WebSocket Notification Failures Silently Swallowed

**What goes wrong:** `realtimeService.sendNotification()` fails but the achievement is still "awarded" - user never sees the badge notification.

**Why it happens:** In `AchievementTriggerServiceImpl.sendBadgeEarnedNotification()`, failure to send WebSocket does not propagate:

```java
// Line 154: If this throws, the achievement is still awarded but caller may not know
realtimeService.sendNotification(userId, payload);  // Fire-and-forget pattern missing try/catch
```

**Prevention:** Log failures explicitly, do not let them cascade:

```java
try {
    realtimeService.sendNotification(userId, payload);
} catch (Exception e) {
    log.warn("Failed to send badge notification to user {}: {}", userId, e.getMessage());
    // Achievement is still awarded - notification is best-effort
}
```

**Phase:** Achievement notification integration phase.

---

### Pitfall 10: Achievement Progress Lost on Concurrent Submissions

**What goes wrong:** User solves 3 problems simultaneously, but `problemsSolvedCount` passed to trigger is stale due to race.

**Why it happens:** Achievement trigger receives a count derived from submissions table that does not account for concurrent submissions by same user.

**Current pattern:**
```java
int count = submissionMapper.countByUserId(userId);  // May not see other in-flight submissions
triggerService.onProblemSolved(userId, count);        // Stale count
```

**Prevention:** Pass the delta, not the absolute:

```java
// Instead of passing current total, trigger checks the actual DB count inside transaction
triggerService.onProblemSolvedAsync(userId);  // Queries current count inside async job
```

**Phase:** Achievement trigger integration with submission flow.

---

## Minor Pitfalls

---

### Pitfall 11: Profile Privacy Settings Missing

**What goes wrong:** No way to make profile/stats private. Users cannot hide their activity from employers viewing public profile.

**Prevention:** Add boolean fields to user profile:
```java
private Boolean profilePublic = true;
private Boolean statsPublic = true;
private Boolean activityPublic = true;
```

**Phase:** User Profile settings phase.

---

### Pitfall 12: Follow System Without Block/Mute

**What goes wrong:** Toxic users can follow victims to harass them via notifications.

**Prevention:** Add block/mute lists before shipping follow feature:
```sql
ALTER TABLE user_follows ADD COLUMN status VARCHAR(20) DEFAULT 'ACTIVE';
-- Blocked users have status = 'BLOCKED'
```

**Phase:** Follow system must include block/mute from MVP.

---

### Pitfall 13: Achievement Display Without Translation

**What goes wrong:** Achievement names/descriptions hardcoded in English, platform has i18n support already.

**Current code:**
```java
// Line 148: achievement.getName() - returns English only
BadgeEarnedPayload.of(achievement.getName(), achievement.getDescription(), ...)
```

**Prevention:** Use existing i18n system:
```java
String name = i18nService.translate("achievement." + achievement.getKey() + ".name", locale);
```

**Phase:** Achievement display phase.

---

## Phase-Specific Warnings

| Phase Topic | Likely Pitfall | Mitigation |
|-------------|---------------|------------|
| Achievement Triggering | Blocking user action on every solve | Make async from day 1 |
| Achievement Data Model | JSON criteria prevents indexing | Add indexed columns for type/target |
| User Profile Stats | Recomputed on every read | Denormalized counters |
| Follow Table | Missing index on (follower, following) | Add composite indexes in migration |
| Follow Fan-out | O(n) write to notify all followers | Async queue-based fan-out |
| Avatar Upload | No server-side validation | Size, type, dimension checks |
| Follow/Unfollow Race | Duplicate follow records | Unique constraint + idempotent method |
| Achievement Progress | Stale count with concurrent submissions | Pass delta, not absolute count |
| WebSocket Notifications | Silent failure on send | Explicit try/catch with logging |
| Achievement i18n | Hardcoded English strings | Use existing i18n service |

---

## Sources

- Context7: Spring Boot async event handling (`@Async`, `ApplicationEventPublisher`)
- Context7: MyBatis-Plus JOIN FETCH patterns
- Official MySQL documentation on composite indexes
- Existing `AchievementTriggerServiceImpl.java` analysis (lines 89-141)
- Existing `Achievement.java` entity analysis (criteria as JSON Map)
- Existing `BadgeEarnedPayload.java` WebSocket notification pattern
