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

---

# Domain Pitfalls: Notification System (v1.7)

**Domain:** WebSocket Notification System for Spring Boot + Vue 3 Platform
**Researched:** 2026-04-21
**Confidence:** HIGH (based on existing WebSocket infrastructure analysis + known notification system pitfalls)

---

## Critical Pitfalls

Mistakes that cause production issues or require significant rework when adding WebSocket notifications to an existing Spring Boot platform.

---

### Pitfall 1: WebSocket Reconnection Storms

**What goes wrong:**
When the server restarts or a WebSocket connection drops, all connected clients attempt to reconnect simultaneously. This thundering herd overwhelms the server, degrading performance for all users even after the server has recovered.

**Why it happens:**
Clients typically reconnect with exponential backoff, but when many users are connected (e.g., during a contest), the aggregate reconnection attempts create a flood. The existing `WebSocketConfig` with STOMP over SockJS does not implement staggered reconnection delays on the client side.

**Current state in codebase:**
- `WebSocketConfig` registers `/ws`, `/ws/contest`, `/ws/notifications` endpoints with SockJS
- `UserSessionManager` tracks sessions in ConcurrentHashMap (in-memory only)
- `WebSocketSessionListener` handles connect/disconnect events but does not implement reconnection throttling

**How to avoid:**
- Add jitter to reconnection attempts on the frontend (random delay between 0 and base delay)
- Implement connection state management that prevents simultaneous reconnects across tabs/users
- Use a message queue (Redis Pub/Sub) to buffer and distribute notifications during reconnection
- Consider server-side connection limits and backpressure

**Warning signs:**
- Spike in `SessionConnectEvent` / `SessionConnectedEvent` handlers after deployment
- `UserSessionManager` showing rapid session churn
- Server CPU/network saturation during normal operation

**Phase to address:**
Phase 1: WebSocket Infrastructure (reconnection logic belongs in frontend and needs to be designed alongside backend)

---

### Pitfall 2: Notification Loss During Disconnect

**What goes wrong:**
Notifications sent while a user is disconnected are lost. The user never sees them upon reconnection unless they poll the REST API manually.

**Why it happens:**
`NotificationService.sendToUser()` uses `SimpMessagingTemplate.convertAndSendToUser()` which delivers only to currently connected sessions. There is no server-side queue persisting messages for offline users. `UserSessionManager` tracks sessions in memory only.

**Current state in codebase:**
- `NotificationService` (in `websocket/NotificationService.java`) sends via `SimpMessagingTemplate`
- Notifications are persisted to DB via `NotificationServiceImpl` (in `modules/notification/service/`)
- `UserSessionManager` has no Redis backing or disk persistence

**How to avoid:**
- Store notifications in the `notifications` table when WebSocket delivery fails
- On reconnect, fetch missed notifications via REST API
- Use a Redis stream or queue as a fallback delivery mechanism
- Add delivery confirmation: only mark notification as "delivered" when client ACKs
- Implement `pending_delivery` column or separate mechanism to track undelivered notifications

**Warning signs:**
- Users reporting missing notifications after being offline
- Notification count mismatches between WebSocket push and REST API query

**Phase to address:**
Phase 2: Notification Persistence (delivery guarantees require database backing)

---

### Pitfall 3: Permission Escalation via Notification Data

**What goes wrong:**
A user can access another user's notifications by manipulating WebSocket message destinations or REST API parameters.

**Why it happens:**
STOMP's `/user/{userId}/queue/notifications` relies on the `JwtChannelInterceptor` to set the user identity. However, the `NotificationController` and `NotificationServiceImpl` must verify ownership on every operation. The existing code in `NotificationServiceImpl` (lines 125-128 and 147-150) already verifies ownership - this is good.

**Current state in codebase:**
- `JwtChannelInterceptor` extracts user from JWT and sets session attributes
- `NotificationServiceImpl.updateNotification()` and `deleteNotification()` verify `notification.getUserId().equals(userId)`
- `NotificationController` appears to use `@CurrentUser` or equivalent

**How to avoid:**
- Verify `notification.getUserId().equals(currentUserId)` on every mutation operation (already done - good)
- Ensure `JwtChannelInterceptor` correctly extracts and validates the user from the JWT on every STOMP frame
- Add integration tests that attempt cross-user notification access
- Do not trust user ID from client-sent headers; derive from server-authenticated principal only

**Warning signs:**
- Missing `@CurrentUser` or equivalent principal injection on controller methods
- Notification queries that do not filter by authenticated user ID

**Phase to address:**
Phase 1: WebSocket Infrastructure (auth validation must be verified early)

---

### Pitfall 4: N+1 on Notification List Query

**What goes wrong:**
Fetching a page of notifications triggers N additional queries to load related data (user who triggered the action, problem title, contest name, etc.).

**Why it happens:**
`NotificationServiceImpl.list()` selects notifications and maps them to `NotificationVO` via `toVO()`. The `Notification` entity has a `metadata` Map field but no explicit JOIN FETCH for related entities. If the frontend needs the actor's username, problem title, or contest name embedded in the notification, each notification triggers a separate query.

**Current state in codebase:**
- `Notification` entity has `metadata` Map<String, Object> stored as JSON via `JacksonTypeHandler`
- `toVO()` copies fields directly without loading related entities
- No JOIN FETCH in `NotificationServiceImpl.list()`

**How to avoid:**
- Use MyBatis-Plus with JOIN FETCH in the query wrapper for frequently-accessed notification types
- Denormalize notification data: store `actorUsername`, `problemTitle`, `contestName` directly in the `metadata` Map or as separate columns when the notification is created
- For complex nested data, use a batch query to load all related entities in one query
- Add integration tests that verify query counts

**Warning signs:**
- Notification list endpoint taking >100ms per page
- NotificationVO fields showing as null despite being populated in metadata

**Phase to address:**
Phase 3: Notification Query Optimization (N+1 should be caught before load testing)

---

### Pitfall 5: Redis Pub/Sub Fan-out at Scale

**What goes wrong:**
When a popular user (e.g., with 10,000 followers) posts content, the system must fan out notifications to all 10,000 followers. With the current `NotificationService.sendToUsers()` implementation, this iterates over each userId and calls `convertAndSendToUser()` individually, creating 10,000 broker operations.

**Why it happens:**
`sendToUsers()` in `websocket/NotificationService.java` is a simple loop: `userIds.forEach(userId -> sendToUser(userId, event, data))`. There is no batching, no parallel processing, and no concept of fan-out limits.

**How to avoid:**
- Use Redis Pub/Sub channels per notification type instead of individual user sends
- Have each user's WebSocket session subscribe to relevant channels at connect time
- Implement fan-out limits: queue notifications and process in batches
- Consider a hybrid: WebSocket for online users, REST polling + push for offline users
- For extremely popular users, use a push notification service instead of in-app WebSocket

**Warning signs:**
- Fan-out operations blocking the thread for >1 second
- Spike in `NotificationService` method latency when popular users trigger events

**Phase to address:**
Phase 4: Scalability (fan-out patterns needed before production load)

---

### Pitfall 6: Duplicate Notifications - Idempotency Failure

**What goes wrong:**
The same notification is delivered multiple times to the same user. This happens when the event trigger fires more than once (e.g., duplicate messages in a message queue, retry logic sending the same event twice, or two event listeners for the same trigger).

**Why it happens:**
`AchievementNotificationListener` listens to `AchievementEarnedEvent` and calls `notificationService.createNotification()`. If the achievement trigger fires twice (e.g., due to retry logic in `@Async` processing), two notifications are created. There is no deduplication key.

**Current state in codebase:**
- `AchievementNotificationListener` uses `@Async` and `@EventListener` for achievement notifications
- `NotificationServiceImpl.createNotification()` inserts without checking for duplicates
- `Notification` entity has no `deduplication_key` column

**How to avoid:**
- Add a `deduplication_key` column to the `notifications` table (composite of `user_id + type + category + trigger_id`)
- Before inserting, check if a notification with that key already exists
- Use the event's unique identifier (e.g., `achievementKey + userId`) as the deduplication key
- For follower notifications, use the follow action ID as the deduplication key
- Implement idempotent event handlers using a "processed events" log

**Warning signs:**
- Users receiving multiple identical notifications
- Duplicate entries in `notifications` table with same `user_id`, `type`, `category`, and similar `created_at`

**Phase to address:**
Phase 2: Notification Persistence (deduplication requires database schema)

---

### Pitfall 7: WebSocket Handshake Failures Behind Load Balancers

**What goes wrong:**
WebSocket connections fail to establish when the application is behind a load balancer (e.g., nginx, AWS ALB) because the handshake is not completed correctly. STOMP over SockJS may work around this, but native WebSocket fails.

**Why it happens:**
WebSocket upgrade requires sticky sessions or session affinity. If the load balancer does not support sticky sessions, the initial HTTP upgrade request reaches one server, but subsequent messages may be routed to a different server that does not recognize the session.

**Current state in codebase:**
- `WebSocketConfig` registers `/ws`, `/ws/contest`, and `/ws/notifications` endpoints
- No session affinity configuration exists in the codebase
- PM2 ecosystem does not include load balancer configuration

**How to avoid:**
- Enable sticky sessions on the load balancer (e.g., `ip_hash` in nginx, cookie-based affinity in AWS ALB)
- Use SockJS as a fallback which works better with load balancers via HTTP long-polling
- Configure the WebSocket endpoints to include a session affinity requirement in deployment documentation
- Test WebSocket connectivity in staging environment that mirrors production load balancer setup

**Warning signs:**
- WebSocket connection failures in production but not in local development
- `SessionConnectEvent` firing but subsequent messages not arriving
- Intermittent disconnections

**Phase to address:**
Phase 1: WebSocket Infrastructure (deployment config must be validated before release)

---

### Pitfall 8: Large Unread Counts Causing UI Lag

**What goes wrong:**
The notification bell shows an unread count that becomes very large (e.g., 1,000+). The frontend attempts to display this number, causing layout shifts or lag.

**Why it happens:**
`NotificationServiceImpl.getUnreadCount()` returns a raw count from the database. The frontend fetches this on page load or via WebSocket push. If users accumulate many unread notifications, the count becomes expensive to compute and display.

**How to avoid:**
- Cap the displayed count at 99+ (standard UI pattern)
- Cache the unread count in Redis with short TTL (e.g., 60 seconds)
- Use Redis INCR/DECR for real-time updates instead of recounting from DB on every fetch
- On WebSocket push, increment the cached count rather than recalculating
- Add pagination: only show the most recent 20-50 notifications in the dropdown

**Warning signs:**
- `getUnreadCount` query taking >50ms
- Frontend notification bell causing CLS (Cumulative Layout Shift)
- Unread count API timing out under load

**Phase to address:**
Phase 3: Notification Query Optimization (count caching belongs in performance work)

---

### Pitfall 9: Stale WebSocket Connections - No Heartbeat

**What goes wrong:**
WebSocket connections appear open but the server or client has crashed, or a network partition has occurred. The server still considers the session active, but the client cannot receive messages.

**Why it happens:**
Neither `WebSocketConfig` nor `UserSessionManager` configures a heartbeat mechanism. The STOMP protocol supports heartbeat frames (`PING`/`PONG` events defined in `NotificationEvent`), but no code actually sends them.

**Current state in codebase:**
- `NotificationEvent` enum includes `PONG` event type (line 45)
- `UserSessionManager` tracks sessions in ConcurrentHashMap with no stale detection
- `WebSocketConfig` does not configure heartbeat intervals in `MessageBrokerRegistry`
- SockJS is configured but heartbeat time is not explicitly set

**How to avoid:**
- Configure a heartbeat interval in `MessageBrokerRegistry`: `config.setHeartbeatValue(new long[]{10000, 10000})`
- Implement a scheduled task that sends `PING` messages and marks connections as stale
- Remove stale sessions from `UserSessionManager` after a timeout
- Use the existing `NotificationEvent.PONG` event type for client-side heartbeat responses
- Set SockJS heartbeat intervals: `.withSockJS().setHeartbeatTime(25000)`

**Warning signs:**
- `UserSessionManager.getOnlineUserCount()` showing users who are actually offline
- Messages sent via `NotificationService` not arriving but no disconnect event
- Accumulation of orphaned sessions in `sessionToUser` map

**Phase to address:**
Phase 1: WebSocket Infrastructure (heartbeat must be implemented early)

---

### Pitfall 10: Notification Data Retention - Orphaned Records

**What goes wrong:**
Notifications accumulate indefinitely in the database, consuming storage and slowing down queries. There is no retention policy or cleanup mechanism.

**Why it happens:**
`NotificationServiceImpl.clearAll()` exists but must be called manually by the user. There is no scheduled cleanup of old read notifications. The `Notification` entity has `createdAt` and `updatedAt` fields but no automatic purge.

**How to avoid:**
- Implement a scheduled job to delete read notifications older than 90 days
- Add a retention policy configuration
- Consider archiving important notifications before deletion
- Use a batch delete to avoid locking the table
- Schedule during off-peak hours

**Warning signs:**
- `notifications` table growing beyond 1 million rows
- Query performance degradation on notification list
- Disk space warnings

**Phase to address:**
Phase 4: Scalability (retention policy needed before production at scale)

---

## Technical Debt Patterns

| Shortcut | Immediate Benefit | Long-term Cost | When Acceptable |
|----------|-------------------|----------------|-----------------|
| Skip deduplication keys | Faster initial development | Duplicate notifications frustrate users | Never - adds data integrity issues |
| In-memory unread count | Avoids Redis setup | Incorrect counts, race conditions | Only in MVP with plan to fix |
| No heartbeat | Simpler initial setup | Orphaned sessions, silent message loss | Never in production |
| Single-threaded fan-out | Simpler code | Blocks thread, poor scalability | Only for <100 recipients |
| No retention policy | No cleanup work | Unbounded table growth | Only for <10K users with short retention |
| Skip sticky session config | Avoids LB complexity | WebSocket failures in production | Never in multi-instance deployment |

---

## Integration Gotchas

| Integration | Common Mistake | Correct Approach |
|-------------|----------------|-----------------|
| Spring @Async | @Async methods not respecting transaction boundaries | Ensure database writes commit before @Async fire; use `TransactionTemplate` or `@TransactionalEventListener(phase = AFTER_COMMIT)` |
| Existing Achievement system | Reusing AchievementNotificationListener pattern without deduplication | Review all event listeners for idempotency before adding new ones |
| Redis caching | Caching notification count without invalidation on new notification | Increment/decrement cached count atomically, or invalidate on write |
| STOMP over SockJS | Assuming all network environments support WebSocket upgrade | Always test with SockJS fallback enabled |
| WebSocket auth | Relying on frontend-sent userId instead of JWT-derived identity | Always extract user from server-validated JWT in interceptor |

---

## Performance Traps

| Trap | Symptoms | Prevention | When It Breaks |
|------|----------|------------|----------------|
| Fan-out to many followers | Notification send blocking for >1s | Async queue processing, Redis Pub/Sub channels | >1,000 followers per user |
| Unread count without cache | DB query on every bell icon hover | Redis counter with short TTL | >100 concurrent users |
| N+1 on notification list | O(n) queries for n notifications | JOIN FETCH or denormalization | >10 notifications per page |
| Large metadata Map | Serialization overhead per notification | Store only necessary fields; limit metadata size | >1KB metadata per notification |
| No pagination on REST | Returning all notifications | Cursor-based pagination for real-time data | >1,000 notifications per user |

---

## Security Mistakes

| Mistake | Risk | Prevention |
|---------|------|------------|
| Trusting client-sent userId in WebSocket destination | Users can subscribe to other users' queues | Derive userId only from server-validated JWT principal |
| Missing authorization on REST endpoints | Users can read/modify other users' notifications | Always verify ownership in service layer (already done in NotificationServiceImpl) |
| No rate limiting on notification creation | Spam notification flood | Apply @RateLimit annotation to notification creation endpoints |
| Exposing internal notification metadata | Information disclosure via metadata fields | Validate metadata fields returned to client; never expose internal IDs |
| No CSRF on notification mutation | Cross-site notification triggering | Standard CSRF tokens on POST/PUT/DELETE (already in SecurityConfig) |

---

## UX Pitfalls

| Pitfall | User Impact | Better Approach |
|---------|-------------|-----------------|
| No push notification feedback | Bell icon updates silently, user unsure if sent | Toast confirmation when action triggers notification |
| Infrequent bell refresh | Notifications appear delayed | WebSocket push for real-time bell update + periodic REST polling as fallback |
| No "mark all as read" confirmation | Users lose track of what they read | Show toast/snackbar on mark-all action |
| Deep-linked notification missing context | Clicking notification goes to wrong page | Include enough context in notification to navigate correctly |
| Notification dropdown showing old items first | User sees irrelevant content | Always show newest first, with "View all" link |

---

## "Looks Done But Isn't" Checklist

- [ ] **WebSocket reconnection:** Frontend implements jitter-based reconnection backoff -- verify with network throttling test
- [ ] **Offline delivery:** New notifications during disconnect appear after reconnect -- verify by disconnecting client mid-session
- [ ] **Deduplication:** Same trigger does not produce duplicate notifications -- verify by triggering achievement twice rapidly
- [ ] **Heartbeat:** Stale connections are detected and cleaned up -- verify by killing client process and checking session cleanup
- [ ] **Unread count accuracy:** Bell count matches REST API count after multiple actions -- verify by performing actions and comparing counts
- [ ] **Cross-user access:** User A cannot access User B's notifications via WebSocket or REST -- verify with integration tests
- [ ] **Fan-out performance:** Sending to 1000 users does not block the request thread -- verify with load test
- [ ] **Retention cleanup:** Old read notifications are deleted -- verify by checking scheduled job execution logs

---

## Recovery Strategies

| Pitfall | Recovery Cost | Recovery Steps |
|---------|---------------|----------------|
| Duplicate notifications | LOW | Add deduplication key; existing duplicates require manual cleanup SQL |
| Lost notifications during disconnect | MEDIUM | Implement missed-notification sync on reconnect; manually re-send critical notifications via admin tool |
| Orphaned sessions | LOW | Wait for session timeout; manually clear `userSessions` map if critical |
| Permission escalation | HIGH | Immediately audit notification access logs; revoke and re-authenticate affected users |
| Fan-out timeout | MEDIUM | Retry failed fan-out deliveries with exponential backoff; queue for async processing |
| N+1 on notification list | LOW | Add proper JOIN FETCH; deploy fix; monitor query times |

---

## Pitfall-to-Phase Mapping

| Pitfall | Prevention Phase | Verification |
|---------|------------------|--------------|
| Reconnection storms | Phase 1: WebSocket Infrastructure | Load test with 1000 simultaneous reconnects |
| Notification loss | Phase 2: Notification Persistence | Disconnect/reconnect test; verify no message loss |
| Permission escalation | Phase 1: WebSocket Infrastructure | Security integration tests for cross-user access |
| N+1 on list | Phase 3: Notification Query Optimization | Query count analysis in integration test |
| Fan-out at scale | Phase 4: Scalability | Load test with popular user (10K followers) |
| Duplicate notifications | Phase 2: Notification Persistence | Race condition test with rapid duplicate events |
| Load balancer handshake | Phase 1: WebSocket Infrastructure | Test in multi-instance staging environment |
| Large unread counts | Phase 3: Notification Query Optimization | UI performance test with 1000+ unread |
| Stale connections | Phase 1: WebSocket Infrastructure | Kill client process; verify session cleanup within 2x heartbeat interval |
| Data retention | Phase 4: Scalability | Verify cleanup job runs; monitor table row count over time |

---

## Sources

- Spring WebSocket Documentation (STOMP over SockJS)
- Spring Session WebSocket Support
- Redis Pub/Sub Fan-out Patterns (Martin Kleppmann, "Designing Data-Intensive Applications")
- WebSocket Load Balancer Configuration (nginx, AWS ALB documentation)
- UltiCode existing codebase: `NotificationServiceImpl`, `UserSessionManager`, `AchievementNotificationListener`, `WebSocketConfig`
- WebSocket Security Best Practices (OWASP)

---
*Pitfalls research for: WebSocket Notification System (v1.7)*
*Researched: 2026-04-21*

---

# Domain Pitfalls: v1.9 Performance & Quality

**Domain:** Spring Boot + MyBatis-Plus + MySQL performance optimization and JaCoCo coverage enforcement
**Researched:** 2026-04-22
**Confidence:** MEDIUM-HIGH (based on code analysis of existing implementation)

---

## Critical Pitfalls

### Pitfall 1: Achievement N+1 Query in getUserPoints()

**What goes wrong:** `AchievementServiceImpl.getUserPoints()` (lines 318-330) executes one query per earned achievement:

```java
List<UserAchievement> userAchievements = userAchievementMapper.findByUserId(userId);
int totalPoints = 0;
for (UserAchievement ua : userAchievements) {
    Achievement achievement = achievementMapper.selectById(ua.getAchievementId()); // N+1!
    if (achievement != null && achievement.getPoints() != null) {
        totalPoints += achievement.getPoints();
    }
}
```

If a user has 50 achievements, this executes **51 queries** instead of 1.

**Why it happens:** The loop calls `achievementMapper.selectById()` inside a for-each loop rather than fetching all achievements in a single query.

**Prevention:** Use JOIN FETCH or batch fetch:
```java
// Option 1: JOIN in SQL
@Select("SELECT a.* FROM achievements a " +
        "JOIN user_achievements ua ON a.id = ua.achievement_id " +
        "WHERE ua.user_id = #{userId}")
List<Achievement> findByUserIdWithAchievement(@Param("userId") String userId);

// Option 2: MyBatis-Plus lambdaQuery with in
List<String> achievementIds = userAchievements.stream()
    .map(UserAchievement::getAchievementId)
    .toList();
List<Achievement> achievements = achievementMapper.selectBatchIds(achievementIds);
```

**Detection:** Enable MyBatis-Plus SQL logging and watch for repeated `selectById` calls in loops.

**Phase:** PERF-01 (Achievement N+1 optimization)

---

### Pitfall 2: Follow System N+1 in toUserSummary()

**What goes wrong:** `FollowServiceImpl.toUserSummary()` (lines 166-167) executes 2 count queries per user displayed:

```java
private UserSummaryDTO toUserSummary(User user) {
    // ...
    dto.setFollowerCount(followMapper.countByFollowingId(user.getId()));   // Query 1
    dto.setFollowingCount(followMapper.countByFollowerId(user.getId()));  // Query 2
    return dto;
}
```

When displaying a list of 20 followers, this creates **41 queries** (1 for follows + 20x2 counts).

**Why it happens:** `getFollowers()` and `getFollowing()` use batch user fetch (`selectBatchIds`) but then call count methods per-user in the stream mapping.

**Prevention:** Fetch counts in batch alongside user data:
```java
// In getFollowers(), after fetching users:
List<String> userIds = follows.stream().map(UserFollow::getFollowerId).toList();
Map<String, User> userMap = userMapper.selectBatchIds(userIds).stream()
        .collect(Collectors.toMap(User::getId, u -> u));

// Batch fetch follower/following counts in single query
List<FollowStatsDTO> stats = followMapper.countBatchByUserIds(userIds);
Map<String, FollowStatsDTO> statsMap = stats.stream()
        .collect(Collectors.toMap(FollowStatsDTO::getUserId, s -> s));
```

**Phase:** PERF-02 (Follow System optimization) - should address both index AND N+1 in same phase.

---

### Pitfall 3: Manual OFFSET Pagination Performance

**What goes wrong:** `FollowMapper` uses manual OFFSET pagination:

```java
@Select("SELECT * FROM user_follows WHERE following_id = #{followingId} ORDER BY created_at DESC LIMIT #{offset}, #{limit}")
List<UserFollow> selectByFollowingIdPaged(@Param("followingId") String followingId, @Param("offset") long offset, @Param("limit") long limit);
```

OFFSET with large values (e.g., page 1000) is **O(offset)** - MySQL must scan and discard rows before returning results.

**Why it happens:** MyBatis-Plus pagination plugin is configured but manual SQL overrides it for these queries.

**Prevention:** Use keyset/cursor pagination instead of OFFSET:
```java
// Cursor-based: use last seen created_at as anchor
@Select("SELECT * FROM user_follows WHERE following_id = #{followingId} " +
        "AND created_at < #{cursor} ORDER BY created_at DESC LIMIT #{limit}")
List<UserFollow> selectByFollowingIdCursor(@Param("followingId") String followingId,
                                          @Param("cursor") LocalDateTime cursor, @Param("limit") int limit);
```

**Phase:** PERF-02 if cursor pagination is implemented; otherwise defer to later phase.

---

## Moderate Pitfalls

### Pitfall 4: Missing Composite Index for Follow Queries

**What goes wrong:** `user_follows` table lacks composite indexes for the most common access patterns:
- `(following_id, created_at DESC)` for `getFollowers()`
- `(follower_id, created_at DESC)` for `getFollowing()`

Current primary key on `(follower_id, following_id)` does not cover ORDER BY efficiently.

**Why it happens:** Indexes were not analyzed when designing the follow queries.

**Prevention:** Add composite indexes in migration:
```sql
CREATE INDEX idx_follows_following_created ON user_follows(following_id, created_at DESC);
CREATE INDEX idx_follows_follower_created ON user_follows(follower_id, created_at DESC);
```

**Column order rule:** MySQL composite indexes are leftmost prefix - `(following_id, created_at)` supports queries filtering on `following_id` alone OR `following_id + created_at`, but NOT `created_at` alone.

**Phase:** PERF-02

---

### Pitfall 5: JaCoCo Verify Phase Enforcement in CI

**What goes wrong:** JaCoCo is configured with `<phase>verify</phase>` in pom.xml but:
1. Maven `verify` phase only runs during full builds, not `mvn test`
2. CI may run `mvn test` without `verify`, skipping coverage enforcement
3. Current thresholds (LINE 0.50, BRANCH 0.40) are too low to enforce quality

**Why it happens:** JaCoCo report is generated but not bound to CI gate.

**Prevention:** Ensure CI invokes `mvn verify` (not just `mvn test`):
```yaml
# GitHub Actions
- name: Build and Test
  run: mvn verify -B  # verify includes test + jacoco
```

**Threshold adjustment:** v1.5 set 50% LINE / 40% BRANCH as initial baseline. v1.9 should raise to 60%/50% as enforcement step.

**Phase:** MISS-01 (JaCoCo enforcement)

---

### Pitfall 6: JaCoCo Coverage Skew from Excluded Classes

**What goes wrong:** pom.xml excludes `*DTO.java`, `*VO.java`, `*Mapper.java`, `*Config.java`, etc. This narrows measured scope, making percentage look higher than actual business logic coverage.

**Why it happens:** Exclusions are reasonable (DTOs/entities have no logic to test) but can mask untested service logic if over-applied.

**Prevention:** Audit exclusions annually. Ensure core service classes (especially `*ServiceImpl.java`) are NOT excluded.

**Phase:** MISS-01

---

### Pitfall 7: MyBatis-Plus Lazy Loading Misconfiguration

**What goes wrong:** If using MyBatis-Plus active-record mode with `aggressive-lazy-loading: true`, accessing nested objects triggers additional queries. Combined with circular references, this causes StackOverflowError.

**Current state:** No aggressive-lazy-loading configured in this project. Low risk.

**Prevention:** Keep default `aggressive-lazy-loading: false` and explicitly choose JOIN FETCH where needed.

**Phase:** Low risk for current codebase

---

## Minor Pitfalls

### Pitfall 8: Follow Stats Race Condition

**What goes wrong:** `FollowServiceImpl.triggerFollowerAchievement()` is `@Async` and calls `getFollowStats()` which queries live counts. Counts may be inconsistent if follow/unfollow is concurrent.

**Why it happens:** Async achievement trigger reads stats that may change before achievement check runs.

**Prevention:** Accept eventual consistency for achievement counts, or pass delta instead of re-querying.

**Phase:** Low priority

---

### Pitfall 9: Pagination Total Count Performance

**What goes wrong:** `FollowMapper.countByFollowingId()` and `countByFollowerId()` run separate COUNT queries. For high-traffic endpoints, these add load.

```java
List<UserFollow> follows = followMapper.selectByFollowingIdPaged(userId, offset, currentPageSize);
long total = followMapper.countByFollowingId(userId);  // Second query
```

**Prevention:** If eventual consistency is acceptable, cache counts in Redis with short TTL (30 seconds).

**Phase:** Defer unless profile shows this is a bottleneck

---

## Phase-Specific Warnings

| Phase Topic | Likely Pitfall | Mitigation |
|-------------|---------------|------------|
| PERF-01: Achievement JOIN FETCH | Forgetting to remove loop after adding JOIN | Verify SQL log shows single query; add integration test with 10+ achievements |
| PERF-01: Achievement JOIN FETCH | JOIN producing duplicate rows if user has multiple user_achievement records | Use SELECT DISTINCT or GROUP BY achievement_id |
| PERF-02: Follow Composite Index | Adding index on (created_at, following_id) instead of (following_id, created_at) | Remember: leftmost prefix rule - filter column must be first |
| PERF-02: Follow Composite Index | Forgetting to ANALYZE TABLE after adding index | Run ANALYZE TABLE for MySQL to update statistics |
| PERF-02: Cursor Pagination | Cursor pagination breaks if rows are deleted | Use stable cursor (ID or timestamp of last row, not offset) |
| MISS-01: JaCoCo Enforcement | CI running `mvn test` instead of `mvn verify` | Change CI step to `mvn verify` |
| MISS-01: JaCoCo Enforcement | JaCoCo report not generated if tests fail | Use `mvn verify` even when some tests fail; JaCoCo captures partial coverage |
| MISS-01: JaCoCo Enforcement | Coverage % drops when new code is added without tests | Raise thresholds incrementally (50% -> 55% -> 60%) |

---

## Verification Checklist

Before marking PERF-01 complete:
- [ ] SQL logs show single query for getUserPoints (no N+1)
- [ ] getUserProgress does not regress (already optimized)

Before marking PERF-02 complete:
- [ ] EXPLAIN shows index usage for follower/following queries
- [ ] No N+1 in toUserSummary (counts batched or pre-computed)
- [ ] Cursor pagination implemented for large offset values (or deferred)

Before marking MISS-01 complete:
- [ ] CI `mvn verify` step passes with new thresholds
- [ ] JaCoCo report generated and accessible in CI artifacts
- [ ] Coverage percentage is measured, not just enforced

---

## Sources

- MyBatis-Plus Pagination: Context7 `/baomidou/mybatis-plus-doc`
- MySQL Composite Index Column Order: MySQL 8.0 Reference Manual - Multiple-Column Indexes
- JaCoCo Enforcement: [JaCoCo Maven Plugin Documentation](https://www.jacoco.org/jacoco/trunk/doc/maven.html)
- MySQL Index Merge vs Composite: [MySQL 8.0 Optimization Docs](https://dev.mysql.com/doc/refman/8.0/en/index-merge-optimization.html)
- Code analysis: `AchievementServiceImpl.java`, `FollowServiceImpl.java`, `FollowMapper.java`, `pom.xml` jacoco configuration
