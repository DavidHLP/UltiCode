# C5 Gating — BookmarkServiceImpl Split

**Date:** 2026-07-11
**Source candidate:** `/tmp/architecture-review-20260711-143159.html` §5 (Worth exploring)
**Red team position:** `.claude/reviews/architecture-review-20260711-143159-review.md` §7 (priority #4)

## Decision: deferred, scoped but not started

The original review proposed splitting `BookmarkServiceImpl` (379 lines)
into three modules:
- `QuickFavoritePolicy` (pure state machine, table-testable)
- `BookmarkFolderStore` (lifecycle + default-folder + batch reorder)
- Item CRUD with single ownership check

## Why deferred

This candidate sits behind a SQL change (`UPDATE ... CASE WHEN id=? THEN
sort_order=? ...`) for batched reorder. The red team CR §4 (C5 risks) calls
out that:
- `sort_order` column exists but its index + concurrency constraints are
  unverified.
- Reorder must survive concurrent reorder attempts (unique constraint
  handling on the case expression).
- Ownership-check deduplication (called out as 6× repeats) needs a focused
  refactor that touches the SQL transaction boundary.

These are preconditions for the refactor to land safely; verifying them
takes more context budget than the refactor itself.

## Gating requirement

`BookmarkFolderStore` becomes viable when:

1. **SQL reorder migration is planned** — a Flyway script adding the
   sort_order unique index (if missing) and the batch CASE expression
   helper. Without this, the "reorder becomes one write" claim doesn't
   hold.
2. **Ownership-check refactor scope is bounded** — identify the 6
   call sites and confirm a single helper at the mapper layer (not the
   service) is the right abstraction.

Both migrations are larger than the policy split itself and warrant
their own dedicated sessions.

## Status

- [ ] Implementation deferred
- [ ] revisit when (a) sort_order SQL change is scheduled, or (b) a third
      "lifecycle + reorder + ownership" combo appears