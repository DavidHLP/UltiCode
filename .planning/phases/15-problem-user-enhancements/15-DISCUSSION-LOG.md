# Phase 15: Problem + User Enhancements - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-04-19
**Phase:** 15-problem-user-enhancements
**Mode:** Auto (--auto) — all gray areas selected, recommended options applied

---

## Area: Random Problem (PROB-01)

| Option | Description | Selected |
|--------|-------------|----------|
| SQL RAND() | `ORDER BY RAND()` — simple, works for MVP | ✓ |
| Custom index shuffle | Application-side random with seeded query | |
| Dedicated random endpoint | New controller vs extend existing ProblemService | ✓ (extend existing) |

**Decision:** `GET /problems/random` via ProblemService.findRandomPublished() with SQL ORDER BY RAND()
**Auto rationale:** Simplest approach that satisfies the requirement without over-engineering.

---

## Area: Acceptance Rate Calculation (PROB-02)

| Option | Description | Selected |
|--------|-------------|----------|
| Stored column | Update `acceptance_rate` on each submission | |
| Computed per query | SQL aggregation on submissions table per request | ✓ |
| Cached with TTL | Compute once, cache in Redis, invalidate on new submission | |

**Decision:** Calculate on read via SQL aggregation — no stale data, no cache invalidation complexity
**Auto rationale:** Simpler than cache invalidation, accurate by default.

---

## Area: Admin Bulk Operations (PROB-03)

| Option | Description | Selected |
|--------|-------------|----------|
| Per-item response | `{ results: [{ id, success, error? }] }` — partial success supported | ✓ |
| Full rollback | All-or-nothing transaction | |
| Single action per request | Separate endpoints per action type | |

**Decision:** Per-item response with partial success — aligns with existing frontend `bulkAction()` UI
**Auto rationale:** Frontend already expects per-item results from `problemsApi.bulkAction()`.

---

## Area: Extended Problem DTO (PROB-04)

| Option | Description | Selected |
|--------|-------------|----------|
| Extend CreateProblemDTO | Add summary, content, examples, constraints, hints, languages, tags | ✓ |
| Separate CreateDetailDTO | New DTO for ProblemDetail, keep CreateProblemDTO minimal | |
| JSON field approach | Single JSON field for all extended data | |

**Decision:** Extend CreateProblemDTO — aligns with admin frontend `bulkEdit()` needing full problem fields
**Auto rationale:** Admin create page needs all fields; extending existing DTO is straightforward.

---

## Area: User Global Rank (USER-01)

| Option | Description | Selected |
|--------|-------------|----------|
| Window function | `RANK() OVER (ORDER BY rating DESC)` in SQL | ✓ |
| Application-side sort | Load all rankings, sort in Java | |
| Separate rank column | Store rank in global_rankings, update on rating change | |

**Decision:** Window function — single query, always current, no denormalization
**Auto rationale:** Standard SQL approach, efficient, no stale rank data.

---

## Area: User Acceptance Rate (USER-02)

| Option | Description | Selected |
|--------|-------------|----------|
| SQL aggregation | `SUM(status='Accepted') / COUNT(*)` per user | ✓ |
| Submission count + separate query | Two queries: total and accepted | |
| Cached in user record | Store acceptance_rate in users table | |

**Decision:** SQL aggregation — single query, accurate, no cache invalidation
**Auto rationale:** Consistent with PROB-02 approach (computed on read).

---

## Area: Public User Profile (USER-03)

| Option | Description | Selected |
|--------|-------------|----------|
| Reuse existing endpoints | `GET /users/{id}` + `GET /users/{id}/stats` already exist | ✓ |
| New public profile endpoint | Separate endpoint with limited fields | |
| Frontend-only solution | Single page component, no backend change | |

**Decision:** Backend already has the endpoints — just need console router + view page
**Auto rationale:** Backend infrastructure already in place; minimal new work needed.

---

## Area: Achievement Path Alignment (USER-04)

| Option | Description | Selected |
|--------|-------------|----------|
| Add alias endpoints | `/achievements/my` → existing handler, `/achievements/points` → existing handler | ✓ |
| Change frontend paths | Update console to call `/achievements/user/me` | |
| Redirect at gateway | Nginx/header-level path rewrite | |

**Decision:** Alias endpoints — preserves existing backend paths, adds frontend-compatible routes
**Auto rationale:** Minimal backend change, no frontend changes needed, backward compatible.

---

## Area: User Submission Count (USER-05)

| Option | Description | Selected |
|--------|-------------|----------|
| Add to UserStatsDTO | `submissionCount` field alongside existing stats | ✓ |
| Separate endpoint | `GET /users/{id}/submission-count` | |
| Inline in existing count query | Add COUNT to existing user stats SQL | ✓ (combined) |

**Decision:** Combined into existing UserStatsDTO SQL query — single endpoint, no new routes
**Auto rationale:** Already querying submissions table for acceptance rate; add COUNT to same query.

---

## Summary

**Gray areas resolved:** 9/9 (all)
**Mode:** Auto — all decisions follow recommended defaults
**Deferred ideas noted:** 5 (user comparison, social sharing, followers, problem version history, import/export)

