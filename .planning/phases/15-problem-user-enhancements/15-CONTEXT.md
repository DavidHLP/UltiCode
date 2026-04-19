# Phase 15: Problem + User Enhancements - Context

**Gathered:** 2026-04-19
**Status:** Ready for planning

<domain>
## Phase Boundary

Implement missing backend endpoints for problem browsing and user profile features: random problem API, acceptance rate calculation, admin bulk operations, extended problem creation DTO, user stats enrichment (global rank, acceptance rate, submission count), public user profile page routing, and achievement API path alignment.

**Scope:**
- PROB-01: `GET /problems/random` endpoint returning one random published problem
- PROB-02: Acceptance rate calculation on problem list/detail — compute from `submissions` table (accepted_count / total_count)
- PROB-03: `POST /admin/problems/bulk` endpoint for publish/unpublish/delete/edit difficulty
- PROB-04: Extend `CreateProblemDTO` with summary, content, examples, constraints, hints, languages, tags
- USER-01: Add `globalRank` to `UserStatsDTO` — rank by rating from `global_rankings` table
- USER-02: Add `acceptanceRate` to `UserStatsDTO` — accepted_submissions / total_submissions for user
- USER-03: Add `/users/:id` route to console router + UserProfileView page
- USER-04: Add alias endpoints `/achievements/my` → `/achievements/user/me`, `/achievements/points` → `/achievements/user/me/points`
- USER-05: Add `submissionCount` to `UserStatsDTO` — total submissions from `submissions` table

**Out of scope:**
- User comparison feature — deferred
- Social profile sharing/meta tags — deferred
- Following/followers social graph — deferred
- Version history for problems — deferred
- Problem import/export — deferred
- Flag/moderation system for problems — deferred

</domain>

<decisions>
## Implementation Decisions

### Problem Random (PROB-01)
- **D-01:** `GET /problems/random` — return one random published problem, no auth required
- **D-02:** Uses existing `ProblemService` with new `findRandomPublished()` method — entity query with `isPublished = true`, random order via SQL `ORDER BY RAND()`

### Acceptance Rate Calculation (PROB-02)
- **D-03:** Calculate on read: `acceptance_rate = accepted_count / total_count * 100` from `submissions` table grouped by problem
- **D-04:** Update `ProblemVO` to include `acceptanceRate` field — computed per query, not stored
- **D-05:** Cache-friendly: calculate in SQL aggregation, not application-side loop

### Admin Bulk Operations (PROB-03)
- **D-06:** `POST /admin/problems/bulk` — accepts `{ ids: string[], action: 'publish' | 'unpublish' | 'delete' | 'edit', params?: { difficulty?: string } }`
- **D-07:** Returns `{ results: [{ id, success, error? }] }` — per-item success/failure for partial feedback
- **D-08:** Batch update in MyBatis-Plus via `updateBatchByIds` or `update(null, lambdaUpdate().in(...).set(...)`

### Extended Problem DTO (PROB-04)
- **D-09:** Add fields to `CreateProblemDTO`: summary, content, examples (JSON), constraints, hints (JSON), languages (JSON array), tags (JSON array)
- **D-10:** Update `ProblemService.createProblem()` to accept full problem detail (ProblemDetail + ProblemLanguage + ProblemTagRelation records)
- **D-11:** ProblemDetail stored separately from Problem — one-to-one via `problem_detail` table

### User Global Rank (USER-01)
- **D-12:** `globalRank` in `UserStatsDTO` — computed as `RANK() OVER (ORDER BY rating DESC)` from `global_rankings` table
- **D-13:** Join user with global_rankings on userId, use window function or subquery for rank

### User Acceptance Rate (USER-02)
- **D-14:** `acceptanceRate` in `UserStatsDTO` — user's accepted submissions / total submissions * 100
- **D-15:** Single SQL aggregation: `SUM(status='Accepted') / COUNT(*) * 100` from `submissions` where user_id = ?

### Public User Profile (USER-03)
- **D-16:** `GET /users/{id}` already exists in `UserController` — returns `UserVO` (public fields only)
- **D-17:** Add `/users/:id` route to console router pointing to `UserProfileView.vue`
- **D-18:** `UserProfileView` uses `userApi.getUserById(id)` and `userStatsApi.getStats(id)` — both endpoints already exist

### Achievement Path Alignment (USER-04)
- **D-19:** Add alias endpoints in `AchievementController`:
  - `GET /achievements/my` → delegate to `getCurrentUserAchievements()`
  - `GET /achievements/points` → delegate to `getCurrentUserPoints()`
- **D-20:** Keep existing `/achievements/user/me` and `/achievements/user/me/points` as canonical — add aliases for frontend compatibility

### User Submission Count (USER-05)
- **D-21:** `submissionCount` in `UserStatsDTO` — total submissions from `submissions` table for user
- **D-22:** Add `submissionCount` field to `UserStatsDTO` and populate via SQL count

### Claude's Discretion
- Random problem SQL: `ORDER BY RAND()` sufficient for MVP (not optimized for large datasets)
- Bulk action transaction: each item succeeds/fails independently — no full rollback on single failure
- Achievement alias vs path change: aliases preserve existing backend paths while satisfying frontend

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Existing Backend
- `backend-spring/src/main/java/com/ulticode/modules/problem/entity/Problem.java` — Problem entity with `acceptance_rate` column
- `backend-spring/src/main/java/com/ulticode/modules/problem/dto/CreateProblemDTO.java` — Current limited DTO (only slug/title/difficulty/isPremium/isPublished)
- `backend-spring/src/main/java/com/ulticode/modules/problem/entity/ProblemDetail.java` — Separate detail entity (summary, content, examples, constraints, hints)
- `backend-spring/src/main/java/com/ulticode/modules/problem/entity/ProblemLanguage.java` — Language support entity
- `backend-spring/src/main/java/com/ulticode/modules/problem/entity/ProblemTagRelation.java` — Tag relation entity
- `backend-spring/src/main/java/com/ulticode/modules/problem/controller/ProblemController.java` — Existing problem endpoints
- `backend-spring/src/main/java/com/ulticode/modules/user/dto/UserStatsDTO.java` — Current DTO missing globalRank, acceptanceRate, submissionCount
- `backend-spring/src/main/java/com/ulticode/modules/user/controller/UserController.java` — Has `GET /users/{id}/stats` and `GET /users/{id}` (existing infrastructure)
- `backend-spring/src/main/java/com/ulticode/modules/achievement/controller/AchievementController.java` — Has `/achievements/user/me` and `/achievements/user/me/points`; needs `/achievements/my` and `/achievements/points` aliases

### Existing Frontend
- `console/src/api/problem.ts` — `fetchRandomProblem()` calls `GET /problems/random` (endpoint missing)
- `console/src/api/user.ts` — `getUserById(id)` calls `GET /users/${id}` (endpoint exists)
- `console/src/api/userStats.ts` — `getStats(userId)` calls `GET /users/${userId}/stats` (endpoint exists)
- `console/src/api/achievement.ts` — `getUserAchievements()` calls `GET /achievements/my`, `getUserPoints()` calls `GET /achievements/points` (endpoints missing)
- `console/src/stores/userStats.ts` — Pinia store using `userStatsApi.getStats(authStore.userId)`
- `console/src/views/dashboard/PersonalDashboardView.vue` — Shows stats with easyProgress/mediumProgress/hardProgress/totalProgress
- `management/src/views/problems/composables/useProblemActions.ts` — `bulkAction()` calls `problemsApi.bulkAction()` (endpoint missing)
- `management/src/stores/admin/problems.ts` — `bulkAction()` and `bulkEdit()` methods defined

### Database Schema
- `db-manager/migrations/V1__initial_schema.sql` — `submissions` table with `status` column (Accepted/Wrong Answer/etc.)
- `db-manager/migrations/V1__initial_schema.sql` — `problems` table with `acceptance_rate` column (stored, to be computed)
- `db-manager/migrations/V3__contest_schema.sql` — `global_rankings` table with `rating` column for rank calculation

### Prior Phase Context
- `.planning/phases/14-contest-engine/14-CONTEXT.md` — Rating calculation, real-time ranking, WebSocket push patterns
- `.planning/phases/13-contest-data-layer/13-CONTEXT.md` — Entity/service patterns, MyBatis-Plus usage

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **UserController**: Already has `GET /users/{id}` and `GET /users/{id}/stats` — just need to extend `UserStatsDTO`
- **AchievementController**: Already has the logic for achievements — just need alias endpoints
- **ProblemService**: Has existing problem query infrastructure — new random method fits existing pattern
- **Submission entity**: Has `status` field — can aggregate for acceptance rate

### Established Patterns
- **MyBatis-Plus query patterns**: Use `QueryWrapper` for dynamic SQL, `lambdaQuery()` for type-safe queries
- **Service layer**: Business logic in `*ServiceImpl`, thin controller delegates to service
- **DTO pattern**: Request DTOs with validation annotations (`@NotBlank`, `@Pattern`, etc.)

### Integration Points
- **Console router**: Add `/users/:id` route to `console/src/router/index.ts`
- **UserStatsDTO**: Extend with 3 new fields (globalRank, acceptanceRate, submissionCount)
- **AchievementController**: Add 2 alias endpoints mapping frontend paths to existing handlers

### Critical Gaps
- No `GET /problems/random` endpoint
- No `POST /admin/problems/bulk` endpoint
- `UserStatsDTO` missing globalRank, acceptanceRate, submissionCount
- Achievement paths `/achievements/my` and `/achievements/points` not implemented
- No `/users/:id` route in console router for public profiles

</code_context>

<specifics>
## Specific Ideas

- Acceptance rate formula: `SUM(CASE WHEN status = 'Accepted' THEN 1 ELSE 0 END) / COUNT(*) * 100 AS acceptance_rate`
- Global rank: `SELECT rank FROM (SELECT user_id, RANK() OVER (ORDER BY rating DESC) as rank FROM global_rankings) t WHERE user_id = ?`
- Submission count: Simple `COUNT(*)` from `submissions` WHERE `user_id = ?`

</specifics>

<deferred>
## Deferred Ideas

### Reviewed Todos (not folded)
None — no pending todos matched this phase.

### Scope Creep Redirected
- User comparison feature — new capability, belongs in future phase
- Social profile sharing / meta tags — new capability, belongs in future phase
- Following/followers social graph — new capability, belongs in future phase
- Problem version history (6 endpoints) — deferred to future phase
- Problem import/export — deferred to future phase

</deferred>

---

*Phase: 15-problem-user-enhancements*
*Context gathered: 2026-04-19*
*Auto mode: all gray areas selected, recommended options applied*
