# Feature Landscape: User Profiles, Achievements, and Follow System (v1.6)

**Domain:** Online Programming Platform - User Social Features
**Researched:** 2026-04-21
**Confidence:** HIGH (based on existing codebase analysis + platform conventions)

## Executive Summary

UltiCode v1.6 adds user profiles, achievement/badge system, and follow/social features to the existing platform. The achievement infrastructure is substantially built; the follow system and profile frontend are new. This research maps table stakes vs differentiators, identifies dependencies on existing modules, and surfaces complexity for each feature area.

---

## 1. User Profiles

### Table Stakes (Must-Have)

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| Profile page with avatar | Identity on platform | LOW | User entity already has `avatar` field |
| Display name and username | Basic identification | LOW | User entity has `name` and `username` |
| Bio/description | Context about user | LOW | User entity has `bio` field |
| Join date | Credibility signal | LOW | User entity has `joinedAt` |
| Problems solved count | Core metric | LOW | Aggregatable from submissions |
| Contest participation history | Activity proof | MEDIUM | Depends on contest module |
| Total submissions | Activity signal | LOW | Aggregatable from submissions |
| Preferred language | Helps others understand style | LOW | User entity has `preferredLanguage` |

### Differentiators (Valued but Not Expected)

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| Activity heatmap (GitHub-style) | Visual engagement, gamification | MEDIUM | Requires submission date tracking |
| Real-time stats widget | Freshness, live feel | MEDIUM | Depends on WebSocket infrastructure |
| Social proof badges | Trust signals | LOW | Ties into achievement system |
| Solution showcase | Demonstrates expertise | MEDIUM | Depends on solution module |
| Profile customizability | Self-expression | LOW | Already supports avatar, bio, links |

### Anti-Features (Avoid)

| Anti-Feature | Why Avoid | What to Do Instead |
|--------------|-----------|-------------------|
| Profile themes/customization | Scope creep for v1.6 | Stick to existing avatar/bio/links |
| Profile views counter | Adds complexity, marginal value | Defer to future milestone |
| Endorsements/skill votes | Complex moderation, easy to abuse | Ties to solution quality instead |

### Dependencies on Existing Infrastructure

| Data Needed | Source Module | Status |
|-------------|---------------|--------|
| Problems solved | `submission` module | Aggregation query needed |
| Contest history | `contest` module | Already has rankings |
| Forum posts | `forum` module | Already has post counts |
| Solutions written | `solution` module | Already exists |
| User achievements | `achievement` module | Fully built |

---

## 2. Achievement / Badge System

### Table Stakes (Must-Have)

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| Achievement list page | Browse all badges | LOW | Achievement entity already exists |
| Earned badges on profile | Social proof | LOW | UserAchievement entity exists |
| Badge tier levels (bronze/silver/gold/platinum) | Progression feel | LOW | Achievement.tier already modeled |
| Points display | Accumulation reward | LOW | Achievement.points exists |
| Real-time badge notification | Delight moment | MEDIUM | WebSocket already built (BadgeEarnedPayload) |

### Differentiators (Valued but Not Expected)

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| Progress indicators (X/Y toward next badge) | Motivation to continue | MEDIUM | AchievementTriggerService can be extended |
| Rare/exclusive badge designation | Status symbol | LOW | Add `isExclusive` flag to Achievement |
| Badge categories with filtering | Browsability | LOW | Achievement.category already exists |
| Achievement streaks | Daily engagement hook | MEDIUM | STREAK_DAYS trigger already exists |
| Contest ranking achievements | Competitive recognition | MEDIUM | CONTEST_WINS, CONTEST_PLACED triggers exist |
| Social sharing of achievements | Viral growth | MEDIUM | Share link generation |
| Achievement leaderboard | Competition between users | MEDIUM | Aggregatable from UserAchievement |

### Existing Achievement Types (Already Built)

Based on `AchievementType.java` and `AchievementTriggerServiceImpl`:

| AchievementType | Trigger | Status |
|-----------------|---------|--------|
| PROBLEMS_SOLVED | onProblemSolved | Built |
| SUBMISSIONS_MADE | onSubmissionMade | Built |
| CONTEST_PARTICIPATION | onContestJoined | Built |
| CONTEST_WINS | onContestWon | Built |
| CONTEST_PLACED | onContestPlaced | Built |
| FORUM_POSTS | onForumPostCreated | Built |
| SOLUTIONS_WRITTEN | onSolutionWritten | Built |
| STREAK_DAYS | onStreakUpdated | Built |
| RATING_MILESTONE | onRatingUpdated | Built |

### Missing Achievement Triggers

| Trigger | Use Case | Complexity |
|---------|----------|------------|
| First problem solved | Onboarding win | LOW |
| Language-specific milestones | Python 100, Java 100 | LOW |
| Easy/Medium/Hard problem ratios | Diversity signal | MEDIUM |
| Solution upvotes received | Quality recognition | MEDIUM |
| Follower count milestones | Social growth | LOW (follow system needed first) |

### Anti-Features (Avoid)

| Anti-Feature | Why Avoid | What to Do Instead |
|--------------|-----------|-------------------|
| Paywalled/premium badges | Fractures community | Keep all badges free |
| Badge trading/selling | Gameable, moderation nightmare | N/A |
| Badge revocation | Complexity, user frustration | Keep badges permanent |

### Dependencies on Existing Infrastructure

| Component | Status | What's Needed |
|-----------|--------|---------------|
| Achievement entity | Built | None |
| UserAchievement entity | Built | None |
| AchievementType enum | Built | None |
| AchievementService | Built | None |
| AchievementTriggerService | Built | Extend with missing triggers |
| WebSocket notification | Built | BadgeEarnedPayload already exists |
| AchievementTriggerService events | Built | Publish events on triggers |

---

## 3. Follow System

### Table Stakes (Must-Have)

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| Follow button on user profiles | Core social action | MEDIUM | NEW - needs module |
| Followers list (who follows me) | Social proof | MEDIUM | NEW - needs module |
| Following list (who I follow) | Social graph | MEDIUM | NEW - needs module |
| Follower/following count on profile | Quick social signal | LOW | NEW - aggregate queries |
| Unfollow action | Flexibility | LOW | NEW |

### Differentiators (Valued but Not Expected)

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| Activity feed | Reasons to return | HIGH | NEW - significant complexity |
| Follow notifications | Engagement | MEDIUM | NEW - ties to notification module |
| Mutual followers highlighting | Social connection | LOW | NEW |
| Suggested users to follow | Discovery | HIGH | Could use recommendation service |
| Follow user activity timeline | See what others solved | MEDIUM | NEW - activity aggregation |

### Anti-Features (Avoid)

| Anti-Feature | Why Avoid | What to Do Instead |
|--------------|-----------|-------------------|
| Private accounts | Complexity, moderation | Keep all accounts public for v1.6 |
| Block functionality | Scope creep | Defer to v1.7 moderation |
| Follow requests (approval) | Adds friction, complexity | N/A |
| Follower counts hidden | Counter to social proof | Show publicly |

### Data Model Recommendations

```
users (existing)
follows
  - id
  - follower_id (user who follows)
  - following_id (user being followed)
  - created_at

Indexes:
- UNIQUE(follower_id, following_id) -- prevent duplicate follows
- INDEX(follower_id) -- who's my following
- INDEX(following_id) -- my followers
```

### Dependencies on Existing Infrastructure

| Component | Status | What's Needed |
|-----------|--------|---------------|
| User entity | Built | None |
| Notification module | Built | Extend for follow notifications |
| WebSocket | Built | Can reuse for real-time feed updates |

---

## 4. Feature Dependencies and Ordering

```
User Profile
├── User data (avatar, bio, links) ────────────────── EXISTING (User entity)
├── Problems solved count ───────────────────────────── EXISTING (aggregation needed)
├── Contest history ────────────────────────────────── EXISTING (contest module)
├── Achievements earned ─────────────────────────────── EXISTING (achievement module)
└── Followers/Following counts ─────────────────────── NEW (follow module)

Achievement System
├── Achievement definitions ─────────────────────────── EXISTING (Achievement entity)
├── User achievements ──────────────────────────────── EXISTING (UserAchievement entity)
├── Achievement triggers ───────────────────────────── EXISTING (AchievementTriggerService)
├── Missing triggers ──────────────────────────────── NEW (extend AchievementTriggerService)
└── Progress indicators ────────────────────────────── NEW (UI + calculation)

Follow System
├── Follow table ─────────────────────────────────── NEW (new module)
├── Follow/Unfollow API ────────────────────────────── NEW (new module)
├── Follower list API ─────────────────────────────── NEW (new module)
├── Following list API ────────────────────────────── NEW (new module)
├── Follow notifications ──────────────────────────── EXISTING (notification module)
└── Activity feed (defer to later) ────────────────── DEFERRED
```

---

## 5. MVP Recommendation

### Phase 1: User Profile (Low Risk, Foundation)
Priority order:
1. Profile API endpoints (GET /users/{id}, PUT /users/{id}/profile)
2. Profile page frontend (avatar, name, bio, links, join date)
3. Stats aggregation (problems solved, contests, submissions)
4. Achievements display on profile

### Phase 2: Achievement System Enhancement (Medium Risk)
Priority order:
1. Missing achievement triggers (first problem, language milestones)
2. Progress indicators for in-progress achievements
3. Achievement notification (already built, verify)
4. Achievement categories with filtering

### Phase 3: Follow System (Medium Risk, Social Layer)
Priority order:
1. Follow table and basic CRUD
2. Follow/Unfollow API
3. Followers/Following list endpoints
4. Follower count on profile
5. Follow notifications

### Defer to v1.7:
- Activity feed (high complexity, new paradigm)
- Suggested users to follow (needs recommendation integration)
- Social sharing of achievements

---

## 6. Complexity Assessment

| Feature | Complexity | Risk | Reason |
|---------|------------|------|--------|
| Profile page frontend | MEDIUM | LOW | Standard CRUD, existing user data |
| Stats aggregation | MEDIUM | MEDIUM | N+1 potential, needs optimization |
| Achievement triggers | LOW | LOW | Pattern already established |
| Progress indicators | MEDIUM | MEDIUM | Calculation logic, caching |
| Follow module | MEDIUM | MEDIUM | New table, new API, consistency |
| Follower list API | LOW | LOW | Standard pagination |
| Activity feed | HIGH | HIGH | Complex aggregation, defer |

---

## 7. Sources

- Existing `backend-spring/src/main/java/com/ulticode/modules/achievement/` (entity, service, trigger)
- Existing `backend-spring/src/main/java/com/ulticode/modules/user/entity/User.java`
- Existing `backend-spring/src/main/java/com/ulticode/modules/websocket/notification/dto/BadgeEarnedPayload.java`
- PROJECT.md v1.6 milestone definition
