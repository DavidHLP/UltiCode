# Phase 38: Achievement N+1 Query Optimization - Context

**Gathered:** 2026-04-22
**Status:** Ready for planning

<domain>
## Phase Boundary

Fix N+1 queries in achievement queries so they execute in constant time regardless of achievement count.

**目标：** Achievement 查询扩展 O(1)，不随成就数量线性增长

</domain>

<decisions>
## Implementation Decisions

### getUserPoints() Batch Strategy
- **D-01:** Use JOIN FETCH strategy — single query with UserAchievementMapper join to achievement table
- **Rationale:** Most efficient, eliminates all per-item queries. MyBatis-Plus can handle JOIN with proper result mapping.

### checkAndAwardAchievements() Batch Strategy
- **D-02:** Use `selectBatchIds` for existing user achievements — fetch all user achievements in one query, then filter in memory
- **Rationale:** Phase 36 used selectBatchIds pattern. Memory filtering is fine for typical user achievement counts (< 100).

### Implementation Pattern
- **D-03:** Create batch fetch utility or reuse existing mapper methods with JOIN FETCH
- **D-04:** AchievementServiceImpl and AchievementTriggerServiceImpl both need fixing
- **D-05:** All tests must pass after refactor (existing AchievementServiceTest)

### Error Handling
- **D-06:** No change to existing error handling — silent failure on achievement check (per Phase 36 decision)

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Phase Context
- `.planning/phases/36-achievement/36-CONTEXT.md` — Phase 36 context (async pattern)
- `.planning/STATE.md` — v1.9 milestone context
- `.planning/ROADMAP.md` — Phase 38 success criteria

### Backend Code (for reference)
- `backend-spring/src/main/java/com/ulticode/modules/achievement/service/impl/AchievementServiceImpl.java` — getUserPoints() N+1 at lines 318-330
- `backend-spring/src/main/java/com/ulticode/modules/achievement/service/impl/AchievementTriggerServiceImpl.java` — checkAndAwardAchievements() N+1 at lines 117-163
- `backend-spring/src/main/java/com/ulticode/modules/achievement/mapper/UserAchievementMapper.java` — existing batch methods
- `backend-spring/src/test/java/com/ulticode/modules/achievement/service/AchievementServiceTest.java` — existing tests

### Java Patterns
- `.claude/rules/java/coding-style.md` — Java 17 record usage, constructor injection
- `.claude/rules/java/patterns.md` — Repository pattern, batch fetch approaches

</canonical_refs>

<codebase_context>
## Existing Code Insights

### Reusable Assets
- `UserAchievementMapper.findByUserId()` — already fetches all user achievements in one query
- `AchievementMapper.findAllActive()` — fetches all active achievements
- Phase 36 async listener pattern (AchievementCheckListener) — established @Async + @TransactionalEventListener

### Established Patterns
- Constructor injection via `@RequiredArgsConstructor` (Lombok)
- MyBatis-Plus `selectBatchIds` for batch fetching
- `userAchievementMapper.findByUserId()` returns `List<UserAchievement>` — all user achievements in one query

### N+1 Locations
1. **AchievementServiceImpl.getUserPoints()** (lines 318-330):
   - Fetches `userAchievementMapper.findByUserId(userId)` → 1 query
   - Then loops and calls `achievementMapper.selectById(ua.getAchievementId())` → N queries
2. **AchievementTriggerServiceImpl.checkAndAwardAchievements()** (lines 142-143):
   - Inside loop, calls `userAchievementMapper.findByUserAndAchievement(userId, achievement.getId())` → N queries

### Integration Points
- AchievementController.getUserPoints() — calls AchievementService.getUserPoints()
- AchievementCheckListener — calls checkAndAwardAchievements() after commit

</codebase_context>

<specifics>
## Specific Ideas

无特殊要求。按以下原则实现：
1. getUserPoints() 用 JOIN FETCH 一次查询
2. checkAndAwardAchievements() 先批量获取已有成就，内存过滤
3. 保持 @Async 异步化（Phase 36 决策）

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope.

</deferred>

---

*Phase: 38-achievement-n+1*
*Context gathered: 2026-04-22*
