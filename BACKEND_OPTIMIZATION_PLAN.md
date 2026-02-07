# UltiCode Backend Optimization Plan

> **Document Purpose**: This document provides a comprehensive optimization roadmap for the UltiCode backend. Each task is designed to give AI implementation maximum freedom while ensuring code quality and consistency.

## Executive Summary

After deep analysis of the backend codebase, the following key areas require attention:

| Category | Priority | Impact | Effort |
|----------|----------|--------|--------|
| Transaction Safety | P0 - Critical | High | Medium |
| Performance Optimization | P1 - High | High | Medium |
| Code Duplication | P2 - Medium | Medium | Low |
| DTO Consistency | P2 - Medium | Medium | Low |
| Service Extraction | P3 - Low | Medium | High |
| API Documentation | P3 - Low | Low | Low |

---

## P0: Critical - Transaction Safety

### Task 1: Implement Transaction Wrappers for Critical Operations

**Problem**: Multi-step database operations lack transaction protection, risking data inconsistency.

**Affected Files**:
- `backend/src/auth/auth.service.ts` - `register()`, `signIn()` methods
- `backend/src/user/user.service.ts` - `changePassword()` method

**Requirements**:
- Wrap multi-step operations in `this.prisma.$transaction()`
- Ensure atomicity for user creation + token generation
- Handle rollback on any failure

**Freedom for AI**:
- Choose between interactive transactions (`$transaction(async (tx) => {...})`) or sequential transactions (`$transaction([...])`) based on what fits best
- Decide on error handling strategy (custom exception types, logging approach)
- May restructure the methods if cleaner patterns emerge
- Can create shared transaction utilities if beneficial

---

## P1: Performance Optimization

### Task 2: Optimize N+1 Query Patterns

**Problem**: Separate queries for related data instead of using Prisma `include`.

**Affected Files**:
- `backend/src/user/user.service.ts` - `getProfileWithRank()` (lines 118-132)
- `backend/src/solution/services/solution-query.service.ts` - fetches all comments just to count them

**Requirements**:
- Use `include` for related data in single queries
- Use `_count` for relationship counts instead of fetching all records

**Freedom for AI**:
- Restructure query patterns as needed
- Create query builder utilities if patterns are repeated
- Decide which relationships benefit from eager vs lazy loading
- May introduce query result caching if beneficial

---

### Task 3: Optimize Database Aggregation Queries

**Problem**: In-memory processing for data that could be aggregated at database level.

**Affected Files**:
- `backend/src/user/user.service.ts` - `getUserStats()` (lines 173-287)
- `backend/src/problem/problem.service.ts` - category filtering done in-memory (lines 88-108)

**Requirements**:
- Move filtering/aggregation into Prisma queries where possible
- Use `groupBy` for counting by difficulty/category
- Reduce data transfer between database and application

**Freedom for AI**:
- Restructure the entire stats gathering approach
- Create dedicated stats service if beneficial
- Choose caching strategy (Redis, in-memory, none)
- Decide on trade-offs between query complexity and performance

---

### Task 4: Implement Caching for Expensive Operations

**Problem**: Frequently accessed, computationally expensive data has no caching.

**Candidates for Caching**:
- User statistics (`getUserStats`)
- Problem lists and counts
- Contest rankings
- Global leaderboards

**Requirements**:
- Use Redis (already configured via BullModule)
- Implement cache invalidation strategy
- Consider cache warming for popular data

**Freedom for AI**:
- Design cache key structure
- Choose TTL values based on data volatility
- Implement decorator-based caching or service-level caching
- Create cache service abstraction if beneficial
- Decide which endpoints benefit most from caching

---

## P2: Code Quality

### Task 5: Extract Duplicated Query Transformation Logic

**Problem**: TypeORM-to-Prisma query transformation duplicated across methods.

**Affected Files**:
- `backend/src/user/user.service.ts` - `findAll()` (lines 48-74) and `count()` (lines 90-112)

**Requirements**:
- Extract shared logic into reusable utility
- Maintain type safety with proper generics

**Freedom for AI**:
- Create utility function, service, or decorator as appropriate
- Decide on placement (common/utils, prisma module, or inline)
- May remove TypeORM compatibility layer if no longer needed
- Design the abstraction to work across all services if pattern exists elsewhere

---

### Task 6: Standardize DTO Usage Across Controllers

**Problem**: Many controllers use inline objects instead of proper DTOs.

**Affected Controllers**:
- `BookmarkController` - `quickFavorite()` uses inline `{ targetType, targetId }`
- `GlobalSolutionController` - `voteSolution()` uses `@Body('voteType')`
- `ViewController` - `recordView()` uses inline body
- `ForumController` - `createComment()`, `updateComment()` use inline objects
- `ProblemListController` - many endpoints use inline bodies
- `SubscriptionController` - `createSubscription()`, `updateSubscription()` use inline objects
- `AdminProblemController` - `flagProblem()` uses inline instead of existing `FlagProblemDto`

**Requirements**:
- Create proper DTO classes with class-validator decorators
- Replace all inline body definitions
- Ensure DTOs are reused where appropriate

**Freedom for AI**:
- Organize DTOs by feature or in common module
- Create base/shared DTOs for common patterns
- Decide on DTO naming conventions
- May consolidate similar DTOs across modules

---

### Task 7: Unify Query Parameter DTOs

**Problem**: Controllers directly use `@Query('param')` instead of encapsulated DTOs.

**Affected Files**:
- `backend/src/submission/submission.controller.ts` - multiple raw query params
- `backend/src/edge-operations/edge-operations.controller.ts` - direct params
- `backend/src/solution/global-solution.controller.ts` - `userId`, `problemId` as raw queries

**Requirements**:
- Create query DTOs that encapsulate related parameters
- Add validation via class-validator
- Use `@Type()` for proper type transformation

**Freedom for AI**:
- Decide on DTO granularity (one per endpoint vs shared)
- May extend existing `PaginationDto` or create new base DTOs
- Choose naming conventions
- Handle optional vs required parameters appropriately

---

## P3: Architecture Improvements

### Task 8: Extract Permission Logic from AuthService

**Problem**: `getUserPermissions()` contains extensive role-permission mapping in AuthService.

**Affected Files**:
- `backend/src/auth/auth.service.ts` - `getUserPermissions()` (lines 234-281)

**Requirements**:
- Move to dedicated permission service or existing `admin/services/permission.service.ts`
- Separate permission definitions from auth logic

**Freedom for AI**:
- Choose target service location
- Design permission configuration (code vs config file vs database)
- May implement more sophisticated RBAC patterns
- Consider permission inheritance or composition

---

### Task 9: Extract User Statistics Service

**Problem**: `UserService.getUserStats()` is large and handles multiple concerns.

**Affected Files**:
- `backend/src/user/user.service.ts` - `getUserStats()` (lines 171-287)

**Requirements**:
- Create dedicated `UserStatsService` or similar
- Separate stats queries from user management

**Freedom for AI**:
- Design service interface and methods
- Choose whether to split by stat type (streaks, activity, solved counts)
- May introduce caching at this layer
- Decide on data structure for stats response

---

### Task 10: Refactor ProblemService Translation Logic

**Problem**: Complex translation and JSON parsing mixed with query logic.

**Affected Files**:
- `backend/src/problem/problem.service.ts` - `findAll()`, `findOne()`

**Requirements**:
- Separate translation concerns from query concerns
- Clean up JSON parsing for translated fields

**Freedom for AI**:
- Create dedicated translation helper or extend I18nService
- May create `ProblemQueryService` for query logic
- Design cleaner translation application pattern
- Choose error handling for invalid JSON

---

### Task 11: Improve Error Handling Consistency

**Problem**: Inconsistent error handling patterns across services.

**Examples**:
- `AuthService.logout()` - swallows errors in catch block (line 108)
- `ProblemService.findOne()` - ignores JSON parsing errors (lines 214-235)

**Requirements**:
- Ensure errors are logged before being handled
- Add appropriate error context
- Use consistent exception types

**Freedom for AI**:
- Design error handling strategy (logging, wrapping, re-throwing)
- Create custom exception types if beneficial
- Implement centralized error logging service if needed
- Decide which errors should be silent vs propagated

---

### Task 12: Add Swagger Documentation Decorators

**Problem**: Incomplete Swagger/OpenAPI documentation.

**Affected Files**:
- Most DTO files lack `@ApiProperty` decorators
- Many controller methods lack `@ApiOperation`, `@ApiResponse`

**Requirements**:
- Add `@ApiProperty` to all DTO fields
- Add controller method documentation
- Document response types

**Freedom for AI**:
- Choose documentation depth (minimal vs comprehensive)
- May generate from existing types where possible
- Decide on documentation organization
- Create documentation utilities if helpful

---

## Database Optimization

### Task 13: Review and Add Missing Indexes

**Candidates for Additional Indexes** (based on query patterns):
- `Problem.category` - for category filtering
- `Notification.type` and `Notification.category` - for independent filtering
- Composite indexes for common query combinations

**Requirements**:
- Analyze actual query patterns
- Add indexes that provide measurable benefit
- Create Prisma migration for new indexes

**Freedom for AI**:
- Use `EXPLAIN ANALYZE` or similar to verify benefit
- Decide on index types (B-tree, hash, etc.)
- Consider partial indexes for common conditions
- May remove unused indexes if found

---

## Technical Debt Cleanup

### Task 14: Remove TypeORM Compatibility Layer

**Problem**: Legacy TypeORM-like query handling exists despite full Prisma migration.

**Affected Files**:
- `backend/src/user/user.service.ts` - `LikeObject` interface and related logic

**Requirements**:
- Verify TypeORM is not used anywhere
- Remove compatibility code
- Update callers to use Prisma patterns

**Freedom for AI**:
- Investigate actual usage of this compatibility layer
- Remove or refactor as appropriate
- May be addressed as part of Task 5

---

### Task 15: Centralize Environment Configuration

**Problem**: Direct `process.env` access scattered across codebase.

**Affected Files**:
- `backend/src/prisma.service.ts`
- `backend/src/auth/services/oauth.service.ts`
- `backend/src/auth/services/cookie.service.ts`
- `backend/src/admin/services/admin-dashboard.service.ts`

**Requirements**:
- Use NestJS `ConfigService` consistently
- Define configuration interface with proper types
- Validate required environment variables at startup

**Freedom for AI**:
- Design configuration structure
- Choose validation approach (Joi, class-validator, custom)
- May create typed config modules for different concerns
- Decide on handling missing/invalid config

---

## Implementation Guidelines

### For AI Implementing These Tasks:

1. **Read First**: Always read the affected files completely before making changes
2. **Test Coverage**: Add or update tests for changed functionality
3. **Type Safety**: Maintain strict TypeScript types - no `any` bypasses
4. **Backwards Compatibility**: Ensure API contracts remain stable unless explicitly changing
5. **Incremental Changes**: Prefer smaller, focused commits over large changes
6. **Documentation**: Update inline comments for complex logic

### Quality Checklist per Task:

- [ ] All affected files read and understood
- [ ] Changes maintain existing behavior
- [ ] Type safety preserved (no eslint-disable for type checks)
- [ ] Tests pass (`npm run test --prefix backend`)
- [ ] Linting passes (`npm run lint`)
- [ ] Type checking passes (`npm run type-check`)

### Commit Convention:

```
feat(scope): add feature
fix(scope): fix issue
refactor(scope): restructure code
perf(scope): improve performance
```

---

## Task Dependencies

```
Task 1 (Transactions)     → Independent
Task 2 (N+1 Queries)      → Independent
Task 3 (Aggregations)     → Independent
Task 4 (Caching)          → After Tasks 2, 3
Task 5 (Query Transform)  → Independent
Task 6 (DTOs)             → Independent
Task 7 (Query DTOs)       → After Task 6
Task 8 (Permissions)      → Independent
Task 9 (User Stats)       → After Task 3
Task 10 (Problem Trans)   → Independent
Task 11 (Error Handling)  → Independent
Task 12 (Swagger)         → After Tasks 6, 7
Task 13 (Indexes)         → After Tasks 2, 3
Task 14 (TypeORM)         → Can merge with Task 5
Task 15 (Config)          → Independent
```

---

## Success Metrics

After implementing these optimizations:

- **Performance**: 50%+ reduction in database round-trips for list endpoints
- **Reliability**: Zero partial-write scenarios through transaction protection
- **Maintainability**: Single-purpose services with clear boundaries
- **Developer Experience**: Complete API documentation, consistent patterns
- **Code Quality**: No duplicated business logic, type-safe throughout
