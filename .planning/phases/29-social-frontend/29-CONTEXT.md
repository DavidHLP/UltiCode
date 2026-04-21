# Phase 29: Social Frontend - Context

**Gathered:** 2026-04-21 (assumptions mode)
**Status:** Ready for planning

<domain>
## Phase Boundary

Social Frontend delivers the profile page and follow button in the Console app. Users can view any user's profile at `/profile/{username}` with header, stats cards, achievements section, and a contextual follow/unfollow button. The follow button shows "Follow" when not following and "Following" with hover-to-unfollow when following. Profile displays earned achievements as cards with badge icons and rarity tiers.

**In scope:**
1. Profile page at `/profile/{username}` with header, stats cards, achievements section
2. Follow button component with state toggle (Follow / Following with hover-to-unfollow)
3. Follower/following count display on profile
4. Achievement cards embedded in profile (reusing existing AchievementCard/AchievementBadge)
5. Follow button visible only when viewing other users' profiles (not own)

**Out of scope:**
- Profile editing (Phase 27 backend exists, frontend edit is separate work)
- Achievement gallery standalone page (already exists at `/achievements`)
- Follower/following list pages (lists endpoint exists, list UI is future work)
- Activity feed / timeline (deferred to v1.7+)
- Avatar upload UI (Phase 27 backend exists, upload UI is separate)

</domain>

<decisions>
## Implementation Decisions

### Route & Page Structure
- **D-01:** New route `/profile/:username` mapping to a new `ProfileView.vue` — cleaner URL than `/users/:id` and matches success criteria
- **D-02:** Existing `UserProfileView.vue` at `/users/:id` remains as-is (it uses numeric ID and has different layout). The new `/profile/:username` route is the social profile page per REQUIREMENTS.md PROFILE-02
- **D-03:** Profile page uses `AppLayout.vue` sidebar layout (consistent with other authenticated pages like personal, forum, contest)

### Follow Button Component
- **D-04:** New `FollowButton.vue` component in `console/src/components/follow/` — isolated, reusable component
- **D-05:** Follow button states: "Follow" (default, outlined primary color) → "Following" (filled, secondary) → hover reveals "Unfollow" (destructive red) — standard Twitter/GitHub pattern
- **D-06:** Follow button hidden when viewing own profile (compare `authStore.user.id` with profile user's ID)
- **D-07:** Optimistic UI update — immediately toggle button state on click, rollback on API error. Show subtle loading spinner during request.

### Follow API Client
- **D-08:** New `console/src/api/follow.ts` with three functions: `followUser(userId)`, `unfollowUser(userId)`, `getFollowStatus(userId)` — the latter checks if current user follows a specific user
- **D-09:** Backend needs one new endpoint: `GET /users/{id}/follow/status` returning `{ isFollowing: boolean }` for the current user. This is required for the follow button to know its initial state when visiting another user's profile

### Achievements on Profile
- **D-10:** Reuse existing `AchievementCard.vue` and `AchievementBadge.vue` components — no new achievement components needed
- **D-11:** Show top 5 earned achievements on profile page with a "View all" link to `/achievements` page
- **D-12:** Fetch achievements via `useAchievementStore` filtering by the viewed user's ID. Need a new API endpoint or param: `GET /achievements/my` currently returns current user's achievements; add `GET /users/{id}/achievements` for viewing any user's achievements

### Profile Data Composition
- **D-13:** Use existing `GET /users/{id}/profile` (Phase 27 `ProfileVO`) for all profile data including `followerCount`, `followingCount`, `achievementCount`
- **D-14:** Follow button fetches its own state separately via `getFollowStatus(userId)` — decoupled from profile data loading

### i18n
- **D-15:** Add follow/unfollow/profile strings to existing locale files (`en-US/personal.ts`, `zh-CN/personal.ts`) — reuse existing `personal` namespace since profile strings are closely related

### Claude's Discretion
- Loading states and skeleton patterns — follow existing `Skeleton` component patterns from `UserProfileView.vue`
- Error handling — follow existing pattern (`ref<string | null>(null)` with try/catch)
- Animation details — CSS transitions for follow button state change (smooth, not jarring)

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Backend APIs (already implemented)
- `backend-spring/src/main/java/com/ulticode/modules/follow/controller/FollowController.java` — Follow/unfollow/list endpoints
- `backend-spring/src/main/java/com/ulticode/modules/follow/dto/FollowStatsDTO.java` — Follow stats response
- `backend-spring/src/main/java/com/ulticode/modules/follow/dto/UserSummaryDTO.java` — User summary for follow lists
- `backend-spring/src/main/java/com/ulticode/modules/user/dto/ProfileVO.java` — Full profile response with social counts
- `backend-spring/src/main/java/com/ulticode/modules/achievement/controller/AchievementController.java` — Achievement endpoints

### Frontend patterns (must follow)
- `console/src/views/users/UserProfileView.vue` — Existing profile view (reference for layout patterns)
- `console/src/views/achievements/AchievementGalleryView.vue` — Achievement page (reuse components)
- `console/src/components/achievement/AchievementCard.vue` — Achievement card component (reuse)
- `console/src/components/achievement/AchievementBadge.vue` — Achievement badge component (reuse)
- `console/src/stores/achievement.ts` — Achievement Pinia store (reuse)
- `console/src/api/achievement.ts` — Achievement API client (reference pattern)
- `console/src/api/user.ts` — User API client (reference pattern, extend for profile)
- `console/src/api/interaction.ts` — Interaction API (reference for follow API pattern)
- `console/src/router/index.ts` — Router (add new route)
- `console/src/views/personal/components/UserProfileCard.vue` — Profile card component (reference)
- `console/src/views/personal/PersonalView.vue` — Own profile page (reference layout)
- `console/src/utils/request.ts` — API request utility (use `apiGet`, `apiPost`, `apiDelete`)

### Requirements
- `.planning/REQUIREMENTS.md` — PROFILE-02, FOLLOW-03
- `.planning/ROADMAP.md` — Phase 29 success criteria
- `.planning/phases/26-follow-system/26-CONTEXT.md` — Follow system context (locked decisions)
- `.planning/phases/27-profile-backend/27-CONTEXT.md` — Profile backend context (locked decisions)
- `.planning/phases/28-achievement-backend/28-CONTEXT.md` — Achievement backend context (locked decisions)

### Project standards
- `.planning/codebase/CONVENTIONS.md` — Coding conventions
- `.planning/codebase/STRUCTURE.md` — Project structure
- `~/.claude/rules/typescript/coding-style.md` — TypeScript/Vue patterns

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **AchievementCard/AchievementBadge**: Full achievement display components with tier, category, progress, earned state — ready to reuse on profile page
- **useAchievementStore**: Pinia store with `fetchUserAchievements()`, `fetchAll()`, WebSocket real-time updates — can fetch and cache user achievements
- **UserProfileCard.vue**: Profile card with avatar, name, bio, social links — similar layout, can reference for styling consistency
- **StatsCard.vue**: Reusable stats card component (used in both `UserProfileView.vue` and `PersonalView.vue`) — reuse directly
- **Progress component**: Already used for difficulty bars — reuse for achievement progress
- **Skeleton component**: Loading pattern — reuse for profile skeleton
- **FeedStatsDTO/ProfileVO**: Backend DTOs already include follower/following counts — no backend changes needed for counts

### Established Patterns
- API client pattern: `apiGet<T>("/endpoint")`, `apiPost<T>("/endpoint", data)`, `apiDelete<T>("/endpoint")` from `@/utils/request`
- Vue component pattern: `<script setup lang="ts">` with `defineProps`, `useI18n`, `useAuthStore`
- Store pattern: Pinia `defineStore` with composition API
- Router pattern: Lazy-loaded routes with `component: () => import(...)`
- Error/loading pattern: `loading ref`, `error ref<string | null>`, try/catch in `onMounted`

### Integration Points
- **New route needed**: `/profile/:username` in `console/src/router/index.ts`
- **New API client needed**: `console/src/api/follow.ts` for follow/unfollow/status
- **New backend endpoint needed**: `GET /users/{id}/follow/status` returning `{ isFollowing: boolean }` — currently no way to check follow status for a specific user without fetching the full follower list
- **Existing backend endpoint used**: `GET /users/{id}/profile` (ProfileVO) for all profile data
- **WebSocket**: Achievement notifications already wired via `getSocketManager()` — follow notifications can use the same pattern if needed later

</code_context>

<specifics>
## Specific Ideas

- Follow button hover-to-unfollow pattern mirrors Twitter/GitHub — the "Following" text smoothly transitions to "Unfollow" on hover with a color shift to red
- Profile page header layout should mirror `UserProfileCard.vue` but add the follow button alongside the user info (right side of the header row)
- Achievement section on profile shows top 5 earned with a "View all achievements" link navigating to the existing `/achievements` page filtered by user
- Route uses `:username` not `:id` per REQUIREMENTS.md "profile page at /profile/{username}" — matches how GitHub and LeetCode handle public profile URLs

</specifics>

<deferred>
## Deferred Ideas

- Follower/following list pages (tabs on profile showing paginated lists) — out of scope, backend supports it but frontend list UI is separate work
- Profile editing UI — Phase 27 backend exists, but frontend edit form is separate from viewing
- Avatar upload UI — backend endpoint exists, upload component is separate from profile display
- Social activity feed/timeline — explicitly deferred to v1.7+ per REQUIREMENTS.md
- Follow notifications (in-app notification for new followers) — backend notification system exists but follow notification UI is separate

</deferred>

---

*Phase: 29-social-frontend*
*Context gathered: 2026-04-21*