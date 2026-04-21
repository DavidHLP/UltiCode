---
status: clean
severity_counts: {CRITICAL: 0, HIGH: 0, MEDIUM: 2, LOW: 2}
---

## Summary

Phase 29 social frontend implementation is well-structured. The backend follow service uses parameterized SQL queries preventing injection, the `isFollowing` implementation is correct, and the follow status endpoint properly requires authentication. Two path ordering issues were identified in AchievementController where `/user/me` is shadowed by `/user/{id}` — these are MEDIUM severity given Spring MVC's path matching behavior. Frontend Vue patterns are solid with proper composable usage at setup level and optimistic update rollback. Two LOW issues relate to follow/unfollow not verifying self-relation and missing prop validation.

## Findings

### [MEDIUM] AchievementController path ordering — `/user/me` endpoint shadowed
**File:** `backend-spring/src/main/java/com/ulticode/modules/achievement/controller/AchievementController.java:71`

**Issue:** `@GetMapping("/user/{id}")` at line 71 will match the literal path `/user/me` before the dedicated `@GetMapping("/user/me")` endpoint at line 43. Spring MVC matches routes in registration order; since both are on the same controller with `@RequestMapping("/achievements")`, the more specific `/user/me` should be registered first, but placing the wildcard `{id}` pattern first is risky if framework behavior differs. Additionally, `/my` at line 59 also shadows `/user/me`.

**Fix:** Reorder so `/user/me`, `/user/me/points`, `/my`, and `/points` all appear before the `/user/{id}` wildcard. Move the `getUserAchievementsById` method to the end of the controller class so its `/{id}` pattern is registered last.

---

### [MEDIUM] Follow/unfollow missing self-relation validation
**File:** `backend-spring/src/main/java/com/ulticode/modules/follow/service/impl/FollowServiceImpl.java:36`

**Issue:** The `follow()` method at line 36 correctly checks `currentUserId.equals(targetUserId)` and throws a `BusinessException`. However, the `unfollow()` method at line 60 has no such guard. While the database `DELETE` query with a non-matching `follower_id` will simply return 0 affected rows (no error), this creates an asymmetry: following yourself is rejected but "unfollowing yourself" silently succeeds. Additionally, if the target user does not exist, `unfollow()` catches the exception and silently continues, whereas `follow()` validates the target first.

**Fix:** Add self-relation check to `unfollow()`: `if (currentUserId.equals(targetUserId)) { throw new BusinessException(ErrorCode.FORBIDDEN, "Cannot unfollow yourself"); }` and validate target existence like `follow()` does.

---

### [LOW] Missing `props` validation in FollowButton
**File:** `console/src/components/follow/FollowButton.vue:7-11`

**Issue:** `defineProps` uses a plain object type annotation without runtime validation. If the parent passes an invalid `targetUserId` (e.g., empty string, wrong type), the error only surfaces at the API call site rather than at component initialization.

**Fix:** Use `defineProps<{ targetUserId: string; initialIsFollowing?: boolean; hidden?: boolean; }>()` with `withDefaults` or add a runtime validator: `targetUserId: { type: String, required: true }`.

---

### [LOW] FollowButton unused `fetchStatus` destructuring
**File:** `console/src/components/follow/FollowButton.vue:14`

**Issue:** `fetchStatus` is destructured from `useFollowStatus` but never called. The parent `ProfileView` calls `fetchStatus()` manually, but `FollowButton` does not use its own copy. This creates confusion about ownership of the fetch lifecycle.

**Fix:** Remove `fetchStatus` from the destructuring since it is unused in this component: `const { isFollowing, loading, toggleFollow } = useFollowStatus(...)`.

---

## Verdict

**Approve** — No CRITICAL or HIGH issues. Two MEDIUM findings are path ordering in AchievementController (which should be resolved before merge due to the `/user/me` shadowing risk) and missing self-relation guard in `unfollow()`. Two LOW findings are non-blocking.
