# Phase 39: Follow System Optimization - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-04-22
**Phase:** 39-follow-system-optimization
**Areas discussed:** toUserSummary() N+1 Fix Strategy, Error Handling

---

## toUserSummary() N+1 Fix Strategy

| Option | Description | Selected |
|--------|-------------|----------|
| JOIN FETCH with GROUP BY | Single query with aggregated counts | ✓ |
| In-memory batch counts | Fetch all counts in 2 queries, then map | |
| Keep current (2N queries) | Do not fix | |

**User's choice:** JOIN FETCH with GROUP BY (auto-selected, recommended default)
**Notes:** Auto mode — recommended default selected.

---

## Error Handling

| Option | Description | Selected |
|--------|-------------|----------|
| Return 0 on failure | Silent fallback | ✓ |
| Throw exception | Propagate error | |
| Return -1 sentinel | Indicate error state | |

**User's choice:** Return 0 on failure (auto-selected, recommended default)
**Notes:** Auto mode — aligns with Phase 36 silent failure pattern.

---

## Claude's Discretion

Areas where downstream agents have flexibility:
- Exact JOIN FETCH query structure (agent can determine best approach)
- Migration file naming convention (agent follows project standards)

