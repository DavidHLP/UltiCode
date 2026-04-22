# Phase 37: Forum Stats 真实数据 - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-04-22
**Phase:** 37-forum-stats
**Areas discussed:** Data Sources, Query Strategy

---

## Data Sources

| Option | Description | Selected |
|--------|-------------|----------|
| forum_comments table | Direct query for comment counts per post | ✓ |
| edge_operations table | Used for votes (forum_votes does not exist) | ✓ |
| No caching | Live queries — sufficient for admin dashboard | ✓ |

**User's choice:** (Auto-selected in --auto mode)
**Notes:** forum_votes table does not exist — votes are stored in edge_operations with target_type FORUM_POST/FORUM_COMMENT

---

## Query Strategy

| Option | Description | Selected |
|--------|-------------|----------|
| MyBatis-Plus LambdaQueryWrapper | Consistent with existing codebase patterns | ✓ |
| DashboardMapper for aggregates | Existing mapper, add new count methods | ✓ |
| ForumStatsMapper or ForumPostMapper for per-post | New query method for comment/vote counts | ✓ |

**User's choice:** (Auto-selected in --auto mode)
**Notes:** Consistent with existing code patterns; no new infrastructure needed

---

## Claude's Discretion

All decisions auto-resolved in --auto mode. No manual choices required.

## Deferred Ideas

None — discussion stayed within phase scope.

