# P0-BASELINE-003 Admin Use-Case Dubbo Call Graph

> status: FROZEN
> head: c344f6268084a893f0bde871da21e5130a331207
> deliverables: Admin use-case RPC graph + 61 reference inventory

## 1. Reference Inventory (current source, not stale graph)

```
grep -rn "@DubboReference" services/admin/src/main/java | wc -l => 61
No Admin reference to Judge (verified via grep -rn "backend-judge" services/admin)
```

Breakdown by group (via `grep -o 'group = "[^"]*"'`):

- `backend-app` : **33** (Dashboard, Forum, Contest, Problem, Solution, Subscription, etc.)
- `backend-auth` : **18** (AccountRead, UserProvisioning, AdminUserProjection, Enricher)
- `backend-submission` : **6** (SubmissionAdminRead, Reconciliation, Streak, UserStats, ActivityAnalytics)
- `backend-notification` (via `NotificationServiceContract.DUBBO_GROUP`) : **4** (NotificationAdminRead, NotificationCutover)

| File | Group | Timeout/Retries | Use-case |
|------|-------|----------------|----------|
| AccountReadAdapter.java:31 | backend-auth | default | admin security |
| DefaultAdminDashboardReadAdapter.java:51,56 | backend-app, backend-auth | default | Dashboard stats |
| DefaultAdminDashboardReadAdapter.java extrapolated | backend-app/app/auth mixed | parallel 3 owners 800ms | Dashboard stats (see §3) |
| AdminUserEnricher.java:75 | backend-auth | QUERY 800ms/1 | Admin user list enrich batch1 |
| AdminUserEnricher.java:79 | backend-app | QUERY 800ms/1 | Admin user list enrich batch2 (currently serial) |
| AdminUserEnricher.java:83 | backend-auth | QUERY 800ms/1 | Admin user list enrich batch3 |
| UserProvisioningAdapter.java:55,59,63 | backend-auth | QUERY/WRITE mixed | user provisioning |
| DubboSubmission*Adapter | backend-submission | default | submission admin |
| ... (full 61 rows retained in grep output, not claimed as production traffic) | | | |

## 2. Use-Case Call Graph (controller -> projection/module -> adapter -> Provider)

### Dashboard (DefaultAdminDashboardReadAdapter.java:51-100, 165-243)
- **Path**: `AdminDashboardController` -> `DefaultAdminDashboardReadAdapter`
- **Fanout**: 3 parallel Owner calls (App stats, Auth user counts, Submission stats) with 800ms per-call timeout, total budget 1.6s (RpcPolicy)
- **Budget**: `RpcPolicy.QUERY_TIMEOUT_MS=800`, `QUERY_RETRIES=1`, `total budget 1.6s`, bulkhead 32, 5 failures open 30s
- **Failure**: partial degradation typed (see AdminUserEnricher pattern)
- **Evidence**: `DefaultAdminDashboardReadAdapter.java:78-100` (parallel stats), `165-243` (trend logic)

### User Trend (currently paginated scan — to be replaced in P3-ADMIN-002)
- **Current**: `DefaultAdminDashboardReadAdapter` user trend loops Auth with `pageSize=100`, serial pages until exhausted
- **Fanout**: `O(N/100)` RPCs, unbounded with account volume (risk: wall time grows linearly)
- **Intervention**: P3-ADMIN-002 replaces with Auth Owner bounded aggregate (`AuthUserTrendAggregateQuery`)

### Admin User List / Enricher (AdminUserEnricher.java:75-83, 208-361)
- **Path**: `AdminUserEnricher` -> 2 batches: Auth identity/account + App profile, merged to `OK/PARTIAL/UNAVAILABLE`
- **Current**: 2 batches currently **serial** (plan §3), to be converged to parallel in P3-ADMIN-003
- **Batch size**: validated via source `Enricher.java:208-361` (batch merge, not per-row RPC)
- **Failure semantics**: `OK` (all), `PARTIAL` (one Provider unavailable, return subset+reason), `UNAVAILABLE` (critical dependency), empty list never means failure

### Other Use-cases (scheduled/batch vs interactive)
- Reconciliation (`OwnerReconciler.java:83`): App + Submission + Notification + Auth, interactive + scheduled
- Cutover services (Contest/Problem/Submission/Notification): each per-Owner, single RPC

## 3. Resilience Budget (RpcPolicy.java:74-100, DubboDependencyResilienceFilter.java)

- Query: 800ms / 1 retry
- Write: higher timeout (per RpcPolicy.WRITE_*)
- Total logical budget: 1.6s
- Bulkhead: 32 concurrent
- Circuit: 5 failures open 30s
- **Note**: dependency budget does not bound page fanout — P3-ADMIN-001 must define use-case wall budget

## 4. Verification

- `grep -rn @DubboReference services/admin/src/main/java` => 61, groups sum to 61
- `grep -rn "backend-judge" services/admin` => 0 (no Judge reference, as claimed)
- `search_graph(name_pattern=".*Admin.*Adapter")` + `trace_path(direction=outbound)` + `get_code_snippet` on `DefaultAdminDashboardReadAdapter`, `AdminUserEnricher` — if stale, direct source read authoritative
- `check_index_coverage` on `services/admin/src/main/java/com/ulticode/modules/admin/projection/AdminUserEnricher.java`, `DefaultAdminDashboardReadAdapter.java`

## Evidence Level

Repository Implemented. Fanout/wall budgets are repository/disposable verifiable, not production SLO proof (excluded_scope).
