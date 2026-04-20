# Phase 26: Follow System - Context

**Gathered:** 2026-04-21
**Status:** Ready for planning

<domain>
## Phase Boundary

Users can follow/unfollow each other and view follower/following lists. User can follow via POST /users/{id}/follow (idempotent, no self-follow). User can unfollow via DELETE /users/{id}/follow. User can view paginated follower list via GET /users/{id}/followers and following list via GET /users/{id}/following. Follower milestone achievements trigger automatically when follow count thresholds are reached via onFollowCountUpdated() in FollowServiceImpl.

</domain>

<decisions>
## Implementation Decisions

### Follow relationship storage
- **D-01:** New `user_follows` table with composite key (follower_id, following_id)
- Uses MyBatis-Plus with a dedicated FollowMapper and FollowService
- Entity: `UserFollow` with followerId, followingId, createdAt fields
- No existing follow table — building from scratch

### Pagination approach
- **D-02:** MyBatis-Plus `Page<T>` with offset pagination (page + size)
- Consistent with existing pagination patterns in the codebase
- Returns total count for both followers and following lists

### Achievement trigger mechanism
- **D-03:** Asynchronous trigger via `@Async` in FollowServiceImpl after follow/unfollow
- `onFollowCountUpdated()` called within the same transaction, async publication
- Prevents blocking the follow response while achievement check runs

### Duplicate follow handling
- **D-04:** Idempotent — if already following, return success without error
- No duplicate key violation thrown; service checks existence before insert
- Returns updated counts regardless of whether follow was new or duplicate

### Response DTOs
- **D-05:** Paginated follower/following lists return UserSummaryDTO per user
- UserSummaryDTO includes: username, avatar, bio (snippet), follow/follower counts
- Consistent with existing user DTO patterns in the codebase

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Backend structure
- `backend-spring/src/main/java/com/ulticode/modules/user/` — User module structure (existing user entity, mapper, service)
- `backend-spring/src/main/java/com/ulticode/modules/subscription/entity/Subscription.java` — MyBatis-Plus entity pattern reference
- `backend-spring/src/main/java/com/ulticode/common/response/Result.java` — API response wrapper pattern
- `.planning/REQUIREMENTS.md` §FOLLOW-01, FOLLOW-02, FOLLOW-04 — Follow system requirements

### No external specs — requirements fully captured in decisions above

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- MyBatis-Plus `BaseMapper` + `IService` + `ServiceImpl` pattern — can follow for new FollowMapper/FollowService
- MyBatis-Plus `Page<T>` pagination — consistent with existing list endpoints
- `UserSummaryDTO` or similar — reuse for follower list items

### Established Patterns
- Controller → Service → Mapper layered architecture
- `@TableLogic` for soft deletes (all entities use this)
- `@Data` Lombok for entities, records for DTOs
- Constructor injection in services (per Java rules)

### Integration Points
- New module: `backend-spring/src/main/java/com/ulticode/modules/follow/`
- User module stays independent — follow is a separate module
- Achievement system: `FollowServiceImpl` calls achievement service after count update

</code_context>

<specifics>
## Specific Ideas

- "onFollowCountUpdated() is called in FollowServiceImpl after each follow/unfollow" — per REQUIREMENTS.md
- No self-follow validation required — service layer checks
- Duplicate follows are idempotent — no error thrown

</specifics>

<deferred>
## Deferred Ideas

- FOLLOW-03: Follow Button State — Phase 29 (frontend)
- Profile view/edit — Phase 27
- Achievement display — Phase 28

</deferred>

---

*Phase: 26-follow-system*
*Context gathered: 2026-04-21*
