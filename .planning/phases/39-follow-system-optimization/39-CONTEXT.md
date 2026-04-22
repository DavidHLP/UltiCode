# Phase 39: Follow System Optimization - Context

**Gathered:** 2026-04-22
**Status:** Ready for planning

<domain>
## Phase Boundary

Optimize follow system queries with composite indexes and eliminate N+1 in toUserSummary().

</domain>

<decisions>
## Implementation Decisions

### toUserSummary() N+1 Fix Strategy
- **D-01:** Use JOIN FETCH with GROUP BY — single query to get all user IDs with their follower/following counts in one round trip
- **Rationale:** Most efficient; avoids the 2N queries when loading user lists. MyBatis-Plus can handle GROUP BY aggregation.

### Error Handling
- **D-02:** No change to existing error handling — follow system continues silent-failure pattern (per Phase 36 decision)

[auto] Area: toUserSummary() N+1 Fix — Q: "Batch count strategy for toUserSummary()?" → Selected: "JOIN FETCH with GROUP BY — single query with aggregated counts" (recommended default)
[auto] Area: Error Handling — Q: "How to handle count failures?" → Selected: "Return 0 on failure — silent fallback" (recommended default)

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Phase Context
- `.planning/phases/38-achievement-n+1/38-CONTEXT.md` — Phase 38 context (batch JOIN FETCH pattern)
- `.planning/ROADMAP.md` — Phase 39 success criteria
- `.planning/STATE.md` — v1.9 milestone context

### Backend Code (for reference)
- `backend-spring/src/main/java/com/ulticode/modules/follow/service/impl/FollowServiceImpl.java` — toUserSummary() N+1 at lines 166-167
- `backend-spring/src/main/java/com/ulticode/modules/follow/mapper/FollowMapper.java` — existing count methods
- `backend-spring/src/main/java/com/ulticode/modules/follow/entity/UserFollow.java` — entity structure
- `db-manager/migrations/V100__follow_schema.sql` — existing schema

### Java Patterns
- `.claude/rules/java/coding-style.md` — Java 17 record usage
- `.claude/rules/java/patterns.md` — Repository pattern, batch operations

</canonical_refs>

<codebase_context>
## Existing Code Insights

### N+1 Location
**toUserSummary()** (lines 156-168):
- For each user in the list, calls `followMapper.countByFollowingId(user.getId())` → N queries
- For each user in the list, calls `followMapper.countByFollowerId(user.getId())` → N queries
- Total: 2N extra queries for N users

### Existing Patterns
- `selectBatchIds` already used for user batch fetching in getFollowers/getFollowing
- Phase 38 used JOIN FETCH strategy for similar batch + aggregation problem

### Integration Points
- FollowController.getFollowers() → FollowService.getFollowers() → toUserSummary()
- FollowController.getFollowing() → FollowService.getFollowing() → toUserSummary()

</codebase_context>

<specifics>
## Specific Ideas

无特殊要求。按以下原则实现：
1. V101 migration 添加 composite indexes: `(following_id, created_at)` 和 `(follower_id, created_at)`
2. toUserSummary() 用 JOIN FETCH + GROUP BY 一次查询获取所有 counts
3. 保持现有的分页逻辑

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope.

</deferred>

---

*Phase: 39-follow-system-optimization*
*Context gathered: 2026-04-22*
