# Phase 37: Forum Stats 真实数据 - Context

**Gathered:** 2026-04-22
**Status:** Ready for planning

<domain>
## Phase Boundary

Fix Admin Dashboard's Forum Stats to return real counts from database queries instead of hardcoded zeros. Specifically:
1. `ForumStats.comments` — query `forum_comments` table (currently uses hardcoded count in `DashboardMapper`)
2. Per-post stats in `AdminForumServiceImpl.toAdminVO()` — `commentCount`, `upvotes`, `downvotes` are all hardcoded to 0

</domain>

<decisions>
## Implementation Decisions

### Data Sources

- **D-01:** Comments count → query `forum_comments` table (`COUNT(*) WHERE post_id = ? AND is_deleted = 0`)
- **D-02:** Post upvotes/downvotes → query `edge_operations` table (`target_type = 'FORUM_POST'`, `operation_type = 'UP_VOTE'` / `'DOWN_VOTE'`)
- **D-03:** Comment upvotes/downvotes → query `edge_operations` table (`target_type = 'FORUM_COMMENT'`, `operation_type = 'UP_VOTE'` / `'DOWN_VOTE'`)
- **D-04:** Stats must be live queries — no caching layer needed for this fix

### Query Strategy

- **D-05:** Use MyBatis-Plus `LambdaQueryWrapper` with `@Select` annotations in existing mappers — consistent with codebase patterns
- **D-06:** For per-post stats, add new query methods to `ForumPostMapper` or create a dedicated `ForumStatsMapper`
- **D-07:** For dashboard-level aggregate stats, add to existing `DashboardMapper` — consistent with existing `countForumComments()` method

### Vote Tracking (edge_operations)

- **D-08:** Votes for forum posts/comments are stored in `edge_operations` table (not `forum_votes`)
- **D-09:** `EdgeOperationTargetType.FORUM_POST` = `"FORUM_POST"` and `EdgeOperationTargetType.FORUM_COMMENT` = `"FORUM_COMMENT"`
- **D-10:** Count upvotes = `COUNT(*) WHERE target_id = ? AND target_type = 'FORUM_POST' AND operation_type = 'UP_VOTE' AND is_deleted = 0` (note: edge_operations may not have `is_deleted` — verify schema)

### Missing Table Alert

- **D-11:** `forum_votes` table does NOT exist in the schema — votes use `edge_operations` table. Do NOT create `ForumVote` entity expecting a `forum_votes` table.

### Test Expectations

- **D-12:** Write unit tests for `AdminForumServiceImpl` that verify real counts are returned instead of zeros
- **D-13:** Test with seeded data: `post-segtree-visual` has 5 comments, `post-rust-hashmap` has 6, `post-contest-tilt` has 7

### Scope Creep Prevention

- No new API endpoints needed — existing endpoints just return wrong values
- No frontend changes — backend fix flows through automatically
- No caching — live queries sufficient for admin dashboard use case

</decisions>

<canonical_refs>
## Canonical References

### Backend Architecture
- `backend-spring/src/main/java/com/ulticode/modules/admin/service/impl/AdminForumServiceImpl.java` — Contains the hardcoded zeros at lines 286-288
- `backend-spring/src/main/java/com/ulticode/modules/admin/service/impl/DashboardServiceImpl.java` — Uses DashboardMapper for forum stats
- `backend-spring/src/main/java/com/ulticode/modules/admin/mapper/DashboardMapper.java` — Existing query methods, needs additional forum stats
- `backend-spring/src/main/java/com/ulticode/modules/admin/dto/DashboardStatsVO.java` — ForumStats VO structure

### Forum Entities
- `backend-spring/src/main/java/com/ulticode/modules/forum/entity/ForumComment.java` — Forum comment entity (maps to `forum_comments` table)
- `backend-spring/src/main/java/com/ulticode/modules/vote/entity/EdgeOperation.java` — Vote storage (maps to `edge_operations` table)
- `backend-spring/src/main/java/com/ulticode/modules/vote/entity/enums/EdgeOperationTargetType.java` — `FORUM_POST`, `FORUM_COMMENT` enum values

### Database Schema
- `db-manager/migrations/V4__forum_schema.sql` — Forum schema with `forum_comments` table definition

### Service Layer
- `backend-spring/src/main/java/com/ulticode/modules/forum/service/ForumVoteService.java` — Existing forum vote service interface
- `backend-spring/src/main/java/com/ulticode/modules/forum/service/impl/ForumVoteServiceImpl.java` — Delegates to VoteService

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `DashboardMapper` already has `countForumComments()` and `countForumPosts()` — pattern established
- `ForumCommentMapper` exists — can add count query methods
- `EdgeOperationMapper` should exist for `edge_operations` table

### Established Patterns
- MyBatis-Plus `LambdaQueryWrapper` for count queries
- `@Select` annotation for simple count queries
- Constructor injection throughout (Lombok `@RequiredArgsConstructor`)

### Integration Points
- `DashboardServiceImpl.getForumStats()` calls `dashboardMapper` — already wired
- `AdminForumServiceImpl.toAdminVO()` — single method needing fix (lines 286-288)
- No new controller endpoints needed

</code_context>

<specifics>
## Specific Notes

- Seeded comment counts: `post-segtree-visual` = 5 comments, `post-rust-hashmap` = 6 comments, `post-contest-tilt` = 7 comments
- 18 total comments seeded across 3 posts
- 4 communities seeded: career (8900 members), compensation (15200), interview (12500), technology (9800)
- `vote_state` field on `forum_posts` exists but stores per-user vote state — NOT aggregate counts
- Aggregate vote counts must come from `edge_operations` table

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope.

</deferred>

---

*Phase: 37-forum-stats*
*Context gathered: 2026-04-22*
