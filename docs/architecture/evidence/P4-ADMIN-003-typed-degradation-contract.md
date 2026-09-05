# P4-ADMIN-003 Typed Degradation Contract

## Status

- **Phase**: P4
- **Area**: AREA-ADMIN
- **Priority**: HIGH
- **Status**: PLANNED → COMPLETE
- **Dependencies**: P4-ADMIN-001, P4-ADMIN-002

## Objective

Distinguish `OK / PARTIAL / UNAVAILABLE` from empty, denied, failed, timed-out, and stale data so that provider failures are never disguised as "no data" and permission failures never surface as empty results.

## Failure Matrix

| error class | source | current mapping | target mapping | HTTP | status |
|---|---|---|---|---|---|
| `OwnerQueryUnavailable` (503) | any owner transport/timeout/refusal | thrown as 503 | thrown as 503 | 503 | UNAVAILABLE |
| `FORBIDDEN` (403) | permission check at owner | thrown as 403 | thrown as 403 (NOT caught/degraded) | 403 | n/a — propagates |
| `UNAUTHORIZED` (401) | auth context missing | thrown as 401 | thrown as 401 (NOT caught/degraded) | 401 | n/a — propagates |
| empty result (0 rows) | owner answered successfully | returned as `total=0`, `degradationStatus=OK` | unchanged | 200 | OK |
| one owner unavailable in multi-owner aggregation | e.g. comment merge, user enricher | varies per projection | partial result returned, `degradationStatus=PARTIAL` | 206 (or 200 with status field) | PARTIAL |
| all owners unavailable in multi-owner aggregation | e.g. both Auth+App down in enricher | thrown as 503 | thrown as 503 | 503 | UNAVAILABLE |
| timeout on bounded query | `CancellableQueryExecutor` | cancels, throws 503 | cancels, throws 503, records `freshness=REQ` | 503 | UNAVAILABLE |
| `null`/`invalid` page from owner | defensive check | varies | treat as UNAVAILABLE for that owner | 503 | UNAVAILABLE |

## Status Merge Semantics (cross-owner)

Pattern established in `DefaultAdminSubmissionProjection.mergeStatus` and `DefaultAdminNotificationProjection.mergeStatus`:

```java
static DegradationStatus mergeStatus(DegradationStatus current, DegradationStatus next) {
    if (current == UNAVAILABLE || next == UNAVAILABLE) {
        throw AdminReadContract.ownerUnavailable("…");  // CRITICAL: fail closed
    }
    if (current == PARTIAL || next == PARTIAL) {
        return PARTIAL;
    }
    return current == null ? OK : current;
}
```

Rules:
1. **UNAVAILABLE is critical-block**: any source UNAVAILABLE → aggregate throws 503
2. **PARTIAL is non-blocking**: some sources degraded → return data with PARTIAL status
3. **OK + OK = OK**: no degradation
4. **null status treated as OK**: backward compatibility for legacy reads

## Per-Use-Case Degradation Status

| use case | aggregation shape | degradation source | status field |
|---|---|---|---|
| `I-USER-LIST` | Auth page + App enrich batch | `AdminUserEnricher.enrichWithStatus` | `PageResult.degradationStatus` |
| `I-USER-DETAIL` | Auth snapshot + App profile + Submission stats + App solution count | `AdminUserDetailResult.availability` → `AdminUserVO` section fields | `AdminUserVO.degradationStatus` + per-section fields |
| `I-DASH-STATS` | App + Submission + Auth parallel | all-or-nothing in `loadStatsInternal` | throws 503 (no partial) — **intentional design** |
| `I-USER-DASHBOARD` | Auth trend via `RpcResult` | `RpcResult.success` check | throws 503 (no partial) |
| `I-FORUM-LIST` | App page + Auth/App enrich batch | `AdminUserEnricher.enrichWithStatus` | `PageResult.degradationStatus` |
| `I-FORUM-DETAIL` | App read + enrichOne | `AdminUserEnricher.findProfileWithStatus` | `AdminUserVO.degradationStatus` |
| `I-SOLUTION-LIST` | App page + enrich batch + problem batch | `AdminUserEnricher` + `ProblemBatch.status()` | `PageResult.degradationStatus` |
| `I-SUBMISSION-LIST` | Submission page + enrich + problem | `AdminUserEnricher` + `ProblemBatch.status()` | `PageResult.degradationStatus` |
| `I-COMMENT-TYPED` | App page + enrich + parent | `AdminUserEnricher` | `PageResult.degradationStatus` |
| `I-COMMENT-ALL` | dual-owner bounded merge (NEW) | `getAllComments` per-moderator try-catch | `PageResult.degradationStatus` |

## I-COMMENT-ALL: New Degradation Pattern

Implemented in `AdminCommentServiceImpl.getAllComments`:

```java
int unavailableCount = 0;
for (CommentModerator moderator : moderators) {
    try {
        PageResult<AdminCommentVO> moderatorPage =
            moderator.listComments(query, 1, MODERATOR_PAGE_SIZE);
        // add items
    } catch (BusinessException e) {
        if (e.getErrorCode() == AdminErrorCode.OWNER_QUERY_UNAVAILABLE) {
            log.warn("Moderator {} unavailable: {}", moderator.getType(), e.getMessage());
            unavailableCount++;
        } else {
            throw e;  // permission failures propagate
        }
    }
}
if (!moderators.isEmpty() && unavailableCount == moderators.size()) {
    throw AdminReadContract.ownerUnavailable("all comment moderators");
}

DegradationStatus mergedStatus = unavailableCount > 0
        ? DegradationStatus.PARTIAL : DegradationStatus.OK;
```

- **Permission failures** (`FORBIDDEN`, `UNAUTHORIZED`, `BAD_REQUEST`): propagate immediately — never caught
- **Transport failures** (`OWNER_QUERY_UNAVAILABLE`): caught per-moderator, aggregated into `PARTIAL`
- **All moderators fail**: throws 503 via `AdminReadContract.ownerUnavailable`
- **Empty moderators list**: returns empty result with `OK` status (no false 503)

## Gaps and Resolution

| gap | affected file | status |
|---|---|---|
| Dashboard `loadStatsInternal` is all-or-nothing (no PARTIAL) | `DefaultAdminDashboardReadAdapter.java:95-131` | **Resolved — intentional design**: Dashboard stats aggregate App + Submission + Auth in 3 parallel futures; all are needed for a coherent view. Single-owner failure → 503. Permission failures (`BusinessException`) are explicitly rethrown (lines 111-112, 123-124), never degraded. No PARTIAL semantics needed — a missing slice means the dashboard is not actionable. |
| `I-CONTEST-LIST` page returns hard `OK` regardless of owner status | `DefaultAdminContestProjection.java:138-144` | **Resolved**: `getContests` propagates `PageResult.degradationStatus` from the App owner (line 57-59, `null` mapped to `OK`). `requirePage` at line 138-144 rejects `UNAVAILABLE` degradation status, throwing `ownerUnavailable` (503) — empty results from a healthy owner (status `null` or `OK`) still return `OK`. |
| `AdminCommentServiceImpl` typed path (`getAllComments`) catches only `BusinessException` — ignores `Error` or unchecked | `AdminCommentServiceImpl.java:208` | **Accepted**: Consistent with `AdminUseCaseMetrics.observe` pattern (catches `RuntimeException | Error`). Permission failures propagate. |

## Verification

- `AdminCommentServiceImplTest`: 3 tests (PARTIAL on one unavailable, 503 on all unavailable, FORBIDDEN propagates) — pass
- `DefaultAdminDashboardReadAdapter`: permission `BusinessException` explicitly rethrown at lines 111-112 and 123-124; `userPoints`/`loadUserData` check `isPermissionError(RpcResult)` before mapping to 503
- `AdminUserEnricherTest`: 6 permission propagation tests (FORBIDDEN/UNAUTHORIZED from direct RPC envelopes and thrown `BusinessException` across `enrichOne`, `enrichWithStatus`, `findAccountWithProfile`) — pass
- `DefaultAdminDashboardReadAdapterUserTrendTest`: 2 permission propagation tests (FORBIDDEN/UNAUTHORIZED from `getUserTrend` RPC envelope → `AdminErrorCode.FORBIDDEN`/`UNAUTHORIZED`, not 503) — pass
- `DefaultAdminSubmissionProjection.mergeStatus`: UNAVAILABLE → throws 503; PARTIAL → propagates; OK + OK → OK
- `DefaultAdminNotificationProjection.mergeStatus`: same pattern
- `DefaultAdminContestProjection.getContests`: propagates `PageResult.degradationStatus` from owner

## HTTP / Status Mapping

| DegradationStatus | HTTP | behavior |
|---|---|---|
| `OK` | 200 | full result |
| `PARTIAL` | 200 | partial result with `degradationStatus: "PARTIAL"` in response body |
| `UNAVAILABLE` | 503 | throws `BusinessException(OWNER_QUERY_UNAVAILABLE)` → mapped by `AdminWebExceptionHandler` |
