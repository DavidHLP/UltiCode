# Phase 29: Social Frontend - Research

**Date:** 2026-04-21
**Phase:** 29 - Social Frontend
**Status:** Research Complete

---

## 1. Existing Frontend Assets

### Profile Page (UserProfileView.vue)
- Located at `/users/:id` route — uses numeric user ID
- Has: avatar, name/username, bio, location, website, join date, stats grid (4 StatsCards), difficulty progress bars, activity heatmap, skill radar chart
- Missing: follower/following counts, follow button, achievements section
- Uses `fetchUserProfile(userId)` which calls `GET /users/{id}` (not ProfileVO)

### Personal Page (PersonalView.vue)
- Own profile page with UserProfileCard, UserStatsPanel
- Uses `fetchUserProfile(currentUserId)` + `fetchUserStats` + `fetchUserSkills`

### Achievement Components
- `AchievementCard.vue` — Full card with badge, name, description, category, progress bar, earned date
- `AchievementBadge.vue` — Badge icon with tier gradient, progress ring, points overlay
- `AchievementGalleryView.vue` — Gallery page with category tabs, stats cards, grid layout
- `useAchievementStore` — Pinia store with `fetchUserAchievements()`, WebSocket real-time updates
- `achievementApi` — `getAll()`, `getUserAchievements()` (calls `/achievements/my`), `getUserPoints()`

### Follow System
- **No frontend follow API client exists** — no `follow.ts` in `console/src/api/`
- **No follow button component** — needs to be built from scratch
- **No follow store** — needs a composable or store for follow state

## 2. Backend API Surface

### Profile (Phase 27 — implemented)
- `GET /users/{id}/profile` → `ProfileVO` with: id, username, name, avatar, bio, company, location, website, joinedAt, preferredLanguage, totalSolved, submissionCount, globalRank, acceptanceRate, followerCount, followingCount, achievementCount

### Follow (Phase 26 — implemented)
- `POST /users/{id}/follow` → `FollowStatsDTO` (followerCount, followingCount)
- `DELETE /users/{id}/follow` → `FollowStatsDTO`
- `GET /users/{id}/followers?page=&pageSize=` → `PageResult<UserSummaryDTO>`
- `GET /users/{id}/following?page=&pageSize=` → `PageResult<UserSummaryDTO>`

### Achievement (Phase 28 — implemented)
- `GET /achievements` → all achievements (with category filter)
- `GET /achievements/{id}` → single achievement
- `GET /achievements/user/me` → current user's achievements with progress
- `GET /achievements/my` → current user's achievements (alias)
- `GET /achievements/points` → current user's points

### User Service
- `findByUsername(String username)` exists in UserService — can resolve username to user ID

## 3. Gap Analysis — Backend Endpoints Needed

| Endpoint | Status | Why Needed |
|----------|--------|------------|
| `GET /users/{id}/follow/status` | **MISSING** | Follow button needs to know if current user follows target user. Without this, button can't show correct initial state |
| `GET /users/{username}/profile` or `GET /users/by-username/{username}/profile` | **MISSING** | Route uses `/profile/:username` but profile endpoint only accepts ID. Need username→profile lookup |
| `GET /users/{id}/achievements` | **MISSING** | Viewing another user's achievements on their profile. Current endpoints only return current user's achievements |

## 4. Frontend Architecture Decisions

### New Files to Create
| File | Purpose |
|------|---------|
| `console/src/api/follow.ts` | Follow API client (follow, unfollow, getStatus) |
| `console/src/components/follow/FollowButton.vue` | Follow/unfollow button component |
| `console/src/views/profile/ProfileView.vue` | New profile page at `/profile/:username` |
| `console/src/composables/useFollowStatus.ts` | Composable for follow state management (lighter than full Pinia store) |

### Existing Files to Modify
| File | Change |
|------|--------|
| `console/src/router/index.ts` | Add `/profile/:username` route |
| `console/src/i18n/locales/en-US/personal.ts` | Add follow/profile i18n strings |
| `console/src/i18n/locales/zh-CN/personal.ts` | Add follow/profile i18n strings |
| `backend-spring/.../follow/controller/FollowController.java` | Add `GET /{id}/follow/status` endpoint |
| `backend-spring/.../user/controller/UserController.java` | Add `GET /by-username/{username}/profile` endpoint |
| `backend-spring/.../achievement/controller/AchievementController.java` | Add `GET /user/{id}` endpoint for any user's achievements |

### Components to Reuse (No Changes)
- `AchievementCard.vue` — display achievements on profile
- `AchievementBadge.vue` — badge icon
- `StatsCard.vue` — stats grid
- `Skeleton` — loading states
- `Progress` — progress bars
- `Avatar` — user avatar display

## 5. Technical Risks

| Risk | Mitigation |
|------|-----------|
| Username route may conflict with other `/profile/*` routes | Use exact match or ensure username pattern doesn't collide |
| Follow status API call on every profile visit adds latency | Cache follow status in composable; use `Promise.all` with profile data |
| Achievement list for other users may be large | Limit to top 5 earned on profile; "View all" goes to gallery page |
| ProfileVO doesn't include `isFollowing` boolean | Separate follow status endpoint — cleaner separation of concerns |

## 6. Pattern References

### API Client Pattern (from existing code)
```typescript
// console/src/api/achievement.ts
import { apiGet } from "@/utils/request";
export const achievementApi = {
  async getAll(params?: AchievementQuery): Promise<AchievementListResult> {
    return apiGet<AchievementListResult>("/achievements", { params });
  },
};
```

### Vue Component Pattern (from existing code)
```vue
<script setup lang="ts">
import { ref, computed, onMounted } from "vue";
import { useI18n } from "vue-i18n";
// ... composable imports
const loading = ref(true);
const error = ref<string | null>(null);
onMounted(async () => { /* fetch data */ });
</script>
```

---

## RESEARCH COMPLETE