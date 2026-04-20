# Project Research Summary

**Project:** UltiCode v1.6 User & Social
**Domain:** Online Programming Platform - Social Features
**Researched:** 2026-04-21
**Confidence:** MEDIUM-HIGH

## Executive Summary

UltiCode v1.6 adds user profiles, achievement/badge system, and follow/social features to an existing Spring Boot + Vue 3 platform. The achievement infrastructure is substantially built (backend scaffolded, V22 migration done); the follow system is net-new. User profiles need targeted enhancements to existing UserVO and UserController.

Research confirms **zero new backend dependencies** are needed. The profile fields already exist on the User entity, the achievement module is already scaffolded, and the follow system uses MyBatis-Plus with a self-referential many-to-many table. Frontend needs only the existing Vue 3 + Tailwind CSS + shadcn-vue stack already in use.

Key risks: (1) Achievement triggering runs synchronously on every submission, causing latency spikes; (2) Follow system fan-out to popular users can exhaust connection pools; (3) Missing database indexes on follow table will cause timeouts on popular users. All three are preventable with async patterns and proper indexing from day one.

## Key Findings

### Recommended Stack

No new dependencies required for v1.6. All features use existing infrastructure.

**Core technologies:**
- **Spring Boot 3.2.5** (not 3.5 — PROJECT.md has stale version) — existing backend framework
- **MyBatis-Plus 3.5.16** — handles all relational operations including composite keys for follow table
- **Spring ApplicationEventPublisher** — existing pattern for async achievement triggering
- **Redis + Redisson 4.3.1** — existing caching layer for profile and follow counts
- **Spring Boot MultipartFile** — built-in file upload for avatar, no new library needed
- **JacksonTypeHandler** — already configured for JSON criteria in achievements

### Expected Features

**Must have (table stakes):**
- Profile page with avatar, bio, links, join date — user entity already has these fields
- Problems solved count, submission count, contest rating — aggregatable from existing tables
- Achievements list and earned badges on profile — achievement module already built
- Follow button, followers list, following list — net-new module needed
- Follower/following counts on profile — aggregate queries needed

**Should have (competitive):**
- Real-time badge notification on achievement earn — WebSocket already built (BadgeEarnedPayload)
- Progress indicators toward next achievement tier — extends AchievementTriggerService
- Achievement categories with filtering — Achievement.category already modeled

**Defer (v2+):**
- Activity feed — high complexity, new aggregation paradigm
- Suggested users to follow — needs recommendation service integration
- Social sharing of achievements — share link generation
- Profile views counter — marginal value, adds complexity

### Architecture Approach

The follow system requires a new `modules/follow/` module with entity, mapper, service, and controller layers. The user profile needs a new `UserProfileVO` record that combines existing UserVO with social stats (follower/following counts). Achievement triggering must be async via `ApplicationEventPublisher` — the existing `AchievementTriggerServiceImpl` runs a full table scan on every event, which will block user actions if not deferred.

**Major components:**
1. **modules/follow/** — new module for follow relationships, counts, lists; integrates with AchievementTriggerService for follower milestones
2. **modules/achievement/** — existing module; needs missing triggers wired (follow count, language milestones) and async event handling verified
3. **modules/user/** — enhanced with UserProfileVO and new `/users/{id}/profile` endpoint returning full social stats

### Critical Pitfalls

1. **Synchronous achievement triggering blocks user actions** — `checkAndAwardAchievements()` does full table scan on every submission. Must use `@Async` event listener pattern from day one.

2. **Fan-out on write explodes for popular users** — naive implementation inserts notification per follower. Must use async queue-based fan-out (`O(1)` write, `O(n)` async delivery).

3. **Follow system missing indexes** — no index on `(follower_id, following_id)` or `(following_id, follower_id)` causes full table scans for popular users. Must add composite indexes in migration.

4. **N+1 queries when loading user achievements** — `UserAchievement` stores only `achievementId`. Must use JOIN FETCH or batch fetch for display.

5. **Achievement criteria JSON prevents database indexing** — `Map<String, Object>` stored as JSON. Must normalize `criteria_type` and `criteria_target` into separate indexed columns.

## Implications for Roadmap

Based on research, suggested phase structure:

### Phase 1: Follow System (New Module)
**Rationale:** New table and module have no dependencies on existing code; creates foundation for social features needed by user profiles and achievement milestones.
**Delivers:** `modules/follow/` with entity, mapper, service, controller; `user_follows` migration with composite indexes; follow/unfollow/follower/following endpoints.
**Avoids:** Pitfall 3 (missing indexes) — add them in migration. Pitfall 2 (fan-out) — use async queue pattern.
**Research flag:** Standard MyBatis-Plus patterns — skip deep research.

### Phase 2: User Profile Enhancements
**Rationale:** Depends on follow system for follower/following counts; uses existing user entity and UserVO.
**Delivers:** `UserProfileVO` record; `GET /users/{id}/profile` endpoint; stats aggregation queries (problems solved, submissions, contest rating).
**Avoids:** Pitfall 6 (recomputed on every read) — use denormalized counters. Pitfall 4 (N+1) — JOIN FETCH for achievements.
**Research flag:** Standard aggregation queries — skip deep research.

### Phase 3: Achievement System Completion
**Rationale:** Achievement module already scaffolded; wire missing triggers and verify async event handling.
**Delivers:** `onFollowCountUpdated()` trigger; missing achievement types (first problem, language milestones); verify async `@Async` event listeners; progress indicator endpoints.
**Avoids:** Pitfall 1 (blocking) — must use `@Async` event listener. Pitfall 5 (JSON criteria) — add indexed columns.
**Research flag:** Spring async event handling already documented — verify implementation only.

### Phase 4: Frontend Integration
**Rationale:** API layer complete; needs UI components for profiles, achievements, and follow.
**Delivers:** Profile page with follow button; achievements display component; follower/following list components.
**Uses:** Existing Vue 3 + Tailwind CSS + shadcn-vue stack.
**Research flag:** Well-documented frontend patterns — skip deep research.

### Phase Ordering Rationale

- **Follow first** because it introduces a new table and module without touching existing code — lowest risk, highest learning value.
- **Profile second** because it depends on follow system for social counts and needs UserProfileVO designed before frontend consumes it.
- **Achievement third** because it needs follow system wired (follower milestones) and async patterns verified before load testing.
- **Frontend last** because it consumes the APIs built in phases 1-3.

### Research Flags

Phases likely needing deeper research during planning:
- **Phase 3 (Achievement Completion):** Async event listener verification — confirm `@Async` is properly configured in current Spring Boot setup; confirm `ApplicationEventPublisher` pattern is used consistently.

Phases with standard patterns (skip research-phase):
- **Phase 1 (Follow System):** MyBatis-Plus composite key patterns well-documented; follow table is straightforward join table.
- **Phase 2 (User Profiles):** Standard Spring Boot controller/service patterns; aggregation queries are well-known.
- **Phase 4 (Frontend):** Existing Vue 3 + shadcn-vue patterns already established in project.

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | HIGH | Zero new dependencies; all verified against existing pom.xml and codebase |
| Features | HIGH | Based on existing achievement module analysis + known platform patterns |
| Architecture | MEDIUM-HIGH | Follow module structure well-defined; achievement async patterns need verification |
| Pitfalls | MEDIUM-HIGH | Based on existing code analysis (AchievementTriggerServiceImpl, User entity) + MySQL best practices |

**Overall confidence:** MEDIUM-HIGH

### Gaps to Address

- **Gap:** Whether `@Async` is properly configured in Spring Boot (need `@EnableAsync` verification)
- **Gap:** Whether current `ApplicationEventPublisher` usage in achievement module follows async pattern
- **Gap:** Frontend component library details for profile page (shadcn-vue components to use)

## Sources

### Primary (HIGH confidence)
- Existing codebase analysis: `backend-spring/src/main/java/com/ulticode/modules/achievement/`, `modules/user/`, `modules/websocket/`
- Existing migrations: `V22__achievement_schema.sql`, `V1__core_schema.sql`
- Existing frontend: `console/src/api/user.ts`, `console/src/api/achievement.ts`
- Existing pom.xml: verified no avatar/upload library present

### Secondary (MEDIUM-HIGH confidence)
- Context7: Spring Boot async event handling (`@Async`, `ApplicationEventPublisher`)
- Context7: MyBatis-Plus JOIN FETCH patterns
- Official MySQL documentation: composite index best practices
- Existing `AchievementTriggerServiceImpl.java` analysis (lines 89-141)
- Existing `BadgeEarnedPayload.java` WebSocket notification pattern

### Tertiary (LOW)
- Community patterns for activity feed — deferred to v2+

---
*Research completed: 2026-04-21*
*Ready for roadmap: yes*
