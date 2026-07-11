# C9 Gating — AdminWritePolicy Speculative

**Date:** 2026-07-11
**Source candidate:** `/tmp/architecture-review-20260711-143159.html` §9 (Speculative)
**Red team position:** `.claude/reviews/architecture-review-20260711-143159-review.md` §3.4 + §7

## Decision: maintain as Speculative, do not implement

The original architecture review proposed unifying the 4 forum toggle methods
(C6) and the 4 problemList update methods into a single `AdminWritePolicy<T>`.
**Red team CR §3.4** verified this proposal rests on a false premise: the two
candidate groups use **two different audit mechanisms** that don't compose:

| Group | Audit mechanism | Code path |
|-------|----------------|-----------|
| forum toggle (`AdminForumServiceImpl`) | `auditHelper.logForUser(...)` — process-style call | `AuditHelper` → `AuditService.log` |
| problemList update (`AdminProblemListServiceImpl`) | `@Audited(action=..., entityType=..., userIdFrom=...)` + `AuditContext.setOldValues/setNewValues` | AOP aspect + ThreadLocal context |

A unified `AdminWritePolicy<T>` would dispatch internally to both mechanisms
via `if (useAuditHelper) { ... } else { AuditContext.setOldValues(...); }` —
**the policy itself becomes shallow**, exactly the failure mode the depth
metric is supposed to detect.

## Gating requirement

`AdminWritePolicy<T>` becomes viable only when one of:

1. **forum toggle migrates to `@Audited`** — the AOP path becomes universal,
   and `auditHelper` is retired for write paths (audit-history reads stay).
2. **problemList migrates to `auditHelper`** — AOP + `AuditContext` are
   retired; the legacy AOP path is dropped.

Both migrations are larger than the forum-toggle collapse (C6) and the
problemList refactor combined. **No implementation until one of the two
migrations lands**; revisit when a third instance of "the same write scaffold"
appears (per the original review's "when a third instance appears" guard).

## What C6 did

C6 (red team priority #1) extracted `ForumPostFieldToggle` + `ForumFlagPolicy`
from `AdminForumServiceImpl`. The six toggle methods became one-line delegates,
the load → snapshot → audit → apply → persist → log pattern moved into the
two policies, and tests for the policy invariants live at the policy
boundary. **C6 did not migrate forum toggle to `@Audited`** — that
migration is the prerequisite for C9 and remains out of scope.

## Evidence of the divergence (red team §A.5)

```
AdminProblemListServiceImpl.java:
  117  @Audited UPDATE_PROBLEM_LIST  → updateListProblems
  155  @Audited DELETE_PROBLEM_LIST  → deleteProblemList       (DELETE, not same shape)
  166  @Audited UPDATE_PROBLEM_LIST  → updateListProblems (?)
  189  @Audited UPDATE_PROBLEM_LIST  → updateBasicInfo
  212  @Audited UPDATE_PROBLEM_LIST  → updateVisibility
  235  @Audited UPDATE_PROBLEM_LIST  → updateBanner

AdminForumServiceImpl.java (C6 baseline):
  pinPost/unpinPost/lockPost/unlockPost/flagPost/unflagPost →
    auditHelper.logForUser(...)
```

## Status

- [x] C6 implemented (forum toggle collapse)
- [ ] C9 implementation deferred until audit-mechanism convergence
- [ ] revisit when (a) third instance appears, or (b) either migration is scheduled