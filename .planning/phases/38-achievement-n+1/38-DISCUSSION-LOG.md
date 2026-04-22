# Phase 38: Achievement N+1 Query Optimization - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-04-22
**Phase:** 38-achievement-n+1
**Areas discussed:** getUserPoints batch strategy, checkAndAwardAchievements batch strategy

---

## getUserPoints Batch Strategy

| Option | Description | Selected |
|--------|-------------|----------|
| JOIN FETCH | Single query with JOIN to achievement table | ✓ |
| selectBatchIds | Batch fetch IDs then lookup | |
| In-memory map | Fetch all, filter in memory | |

**User's choice:** JOIN FETCH (auto-selected, recommended default)
**Notes:** [auto] Most efficient, eliminates all per-item queries. MyBatis-Plus can handle JOIN with proper result mapping.

---

## checkAndAwardAchievements Batch Strategy

| Option | Description | Selected |
|--------|-------------|----------|
| selectBatchIds + memory filter | Fetch all user achievements, filter in memory | ✓ |
| JOIN FETCH | Single query with JOIN | |
| Individual queries (N+1) | Current implementation | |

**User's choice:** selectBatchIds + memory filter (auto-selected, recommended default)
**Notes:** [auto] Memory filtering is fine for typical user achievement counts (< 100). Phase 36 used selectBatchIds pattern.

---

## Claude's Discretion

All decisions auto-selected via `--auto` mode. Standard approaches accepted.

## Deferred Ideas

None — discussion stayed within phase scope.
