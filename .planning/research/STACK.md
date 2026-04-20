# Technology Stack Additions: v1.6 User & Social

**Project:** UltiCode v1.6 User & Social
**Researched:** 2026-04-21
**Confidence:** HIGH

## Executive Summary

The v1.6 User & Social features (user profiles, achievements, follow system) require **zero new backend dependencies**. The profile fields already exist on the User entity, the achievement module is already scaffolded, and the follow system uses MyBatis-Plus with a self-referential many-to-many table. Frontend needs only the existing Vue 3 + Tailwind CSS + shadcn-vue stack already in use.

---

## Existing Stack (No Changes Needed)

| Layer | Technology | Version | Notes |
|-------|------------|---------|-------|
| Backend Framework | Spring Boot | 3.2.5 | NOT 3.5 (PROJECT.md has stale version) |
| ORM | MyBatis-Plus | 3.5.16 | Handles all relational operations |
| Database | MySQL | 8.x | Flyway migrations in place |
| Cache/Locks | Redis + Redisson | 4.3.1 | Rate limiting already working |
| File Upload | Spring Boot multipart | Built-in | `spring-boot-starter-web` includes `MultipartFile` |
| JSON | Jackson | Bundled with Spring Boot | `JacksonTypeHandler` already used in Achievement entity |
| Events | Spring ApplicationEventPublisher | Built-in | Achievement module uses this pattern |
| API Docs | SpringDoc OpenAPI | 2.6.0 | Already configured |

**No version changes required to any existing dependencies.**

---

## New Backend Dependencies: NONE

### Why Zero New Dependencies

| Feature | Implementation | Why No Library Needed |
|---------|---------------|----------------------|
| **Avatar upload** | `MultipartFile` + local storage | Spring Boot web starter handles multipart natively. Store avatars as files, save URL to existing `avatar` column. |
| **Achievements** | MyBatis-Plus entities + Spring Events | Module already scaffolded (`Achievement.java`, `UserAchievement.java`, `AchievementService`). Need only business logic, not libraries. |
| **Follow system** | Self-referential `user_follows` table | MyBatis-Plus handles `@ManyToMany` through join table. No graph DB needed at this scale. |

---

## Avatar Upload: Stack Decision

### Option A: Local File System (Recommended for MVP)

```xml
<!-- No new dependency - use Spring Boot's built-in MultipartFile -->
<!-- Store files in /var/ulticode/uploads/avatars/ or similar -->
```

**Pros:** Zero infrastructure, simple implementation
**Cons:** Requires manual file cleanup, not clustered-friendly
**Verdict:** MVP only. File storage path configured via `app.upload.avatar-dir` in `.env`.

### Option B: MinIO / S3 (Production Path)

```xml
<!-- Only add when local storage becomes a bottleneck -->
<dependency>
    <groupId>io.minio</groupId>
    <artifactId>minio</artifactId>
    <version>8.5.12</version>
</dependency>
```

**Verdict:** Defer to future phase. Current user scale does not warrant object storage.

### What NOT to Add

| Library | Why Avoid |
|---------|-----------|
| `thumbnailator` / `java.imageio` | Offload image resizing to frontend (CSS `object-fit: cover`). Backend stores original only. |
| `spring-cloud-azure-storage` | Overkill for MVP avatar storage |
| `imgscalr` | Same as above - frontend handles display sizing |

---

## Achievement System: Existing Module

The achievement module is already created at `com.ulticode.modules.achievement`:

| File | Status |
|------|--------|
| `Achievement.java` entity | DONE - defines `id, key, name, description, icon, category, tier, criteria, points` |
| `UserAchievement.java` entity | DONE - tracks user-achievement ManyToMany |
| `AchievementService.java` | DONE |
| `AchievementTriggerService.java` | DONE - event-driven triggers |
| `AchievementController.java` | DONE |

**What remains:** Business logic implementation (achievement unlock conditions, progress tracking, notification on unlock).

**No new dependencies needed.** Uses existing:
- `ApplicationEventPublisher` for `AchievementEarnedEvent`
- `JacksonTypeHandler` (already in pom.xml) for `criteria` JSON column
- MyBatis-Plus `ManyToMany` via join entity `UserAchievement`

---

## Follow System: New Module Required

### Database Design

```sql
CREATE TABLE user_follows (
    follower_id   VARCHAR(36) NOT NULL,  -- the user who follows
    following_id  VARCHAR(36) NOT NULL,  -- the user being followed
    created_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (follower_id, following_id),
    INDEX idx_following_id (following_id),
    CONSTRAINT fk_follower FOREIGN KEY (follower_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_following FOREIGN KEY (following_id) REFERENCES users(id) ON DELETE CASCADE
);
```

### Entity Design

```java
@Data
@TableName("user_follows")
@KeySequence("user_follows") // For MySQL sequence
public class UserFollow {
    @TableId(type = IdType.INPUT)
    private String followerId;

    @TableId(type = IdType.INPUT)
    private String followingId;

    private LocalDateTime createdAt;
}
```

**No new libraries needed.** MyBatis-Plus handles composite primary keys via `@TableId(type = IdType.INPUT)`.

---

## Frontend Stack Additions: NONE

| Feature | Frontend Approach | Existing Support |
|---------|------------------|------------------|
| Profile page | Vue 3 component + Tailwind CSS | Already in use |
| Achievement badges | Vue component rendering `icon` URL | Already in use |
| Follow button | Vue reactive component | Already in use |
| Avatar upload | `multipart/form-data` POST to backend | Fetch API already in use |
| Follower/following lists | Paginated API responses | `Result<PageResult<T>>` already exists |

The frontend already has:
- Vue 3 + Vite
- Tailwind CSS v4 with OKLCH design tokens
- shadcn-vue components
- Lucide icons
- Fetch API with request.ts wrapper

**No npm packages needed for v1.6.**

---

## Integration Points

### Backend Controller Entry Points (New)

```
POST   /users/{id}/avatar          # Upload avatar
GET    /users/{id}/profile         # Public profile view
GET    /users/{id}/achievements    # User's earned achievements
GET    /users/{id}/followers       # Paginated follower list
GET    /users/{id}/following       # Paginated following list
POST   /users/{id}/follow          # Follow a user
DELETE /users/{id}/follow          # Unfollow a user
```

### Achievement Integration (Existing Triggers)

Achievement triggers integrate with existing submission/contest modules via `AchievementTriggerService`:

```java
// Integration with SubmissionService - trigger on accepted submission
public void onSubmissionAccepted(String userId, String problemId) {
    eventPublisher.publishEvent(new SubmissionAcceptedEvent(userId, problemId));
}

// AchievementTriggerService listens and updates progress
@EventListener
public void handleSubmissionAccepted(SubmissionAcceptedEvent event) {
    achievementTriggerService.incrementProgress(event.userId(), "problems_solved");
}
```

### Caching (Existing Redis Setup)

Follow counts and achievement progress can use the existing Redis caching layer:

```java
@Cacheable(value = "user:follow:counts", key = "#userId")
public FollowCounts getFollowCounts(String userId);
```

Existing `spring-boot-starter-cache` + Redisson backend already in place.

---

## Anti-Patterns to Avoid

| Anti-Pattern | Why | Instead |
|--------------|-----|---------|
| Adding Spring Social | Deprecated, heavy | Custom follow module with MyBatis-Plus |
| Adding Neo4j/graph DB | Overkill for follower counts | MySQL join table |
| Adding image processing library | Complexity, not needed | Frontend CSS handles sizing |
| Adding notification library | Existing WebSocket module can emit events | Reuse `websocket` module |
| Adding activity feed library | Scope creep for v1.6 | Defer to future phase |

---

## Version Summary

| Component | Current | Needed for v1.6 | Change |
|-----------|---------|-----------------|--------|
| Spring Boot | 3.2.5 | 3.2.5 | None |
| MyBatis-Plus | 3.5.16 | 3.5.16 | None |
| Redisson | 4.3.1 | 4.3.1 | None |
| Hutool | 5.8.44 | 5.8.44 | None |
| Frontend deps | existing | existing | None |

---

## Sources

- Spring Boot 3.2.5 multipart file upload: built-in `MultipartFile` support via `spring-boot-starter-web`
- MyBatis-Plus composite key: `@TableId(type = IdType.INPUT)` for `@ManyToMany` join entities
- Achievement module: verified existing at `com.ulticode.modules.achievement`
- User entity profile fields: verified existing (`avatar`, `bio`, `company`, `github`, `twitter`, `location`)
- pom.xml: verified no avatar/upload library present, none needed
