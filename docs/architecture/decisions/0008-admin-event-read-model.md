# ADR-0008: Keep Admin owner reads synchronous for now

**Date**: 2026-09-02
**Status**: accepted
**Deciders**: Architecture / Admin owners

## Context

Admin owner reads were previously coupled through an Auth account page scan and
serial enrichment calls. The current remediation replaces the trend scan with
one bounded Auth-owned aggregate, runs independent identity/profile batches in a
bounded executor, exposes typed degradation, and records fixed use-case budget
metrics. Admin still needs current owner facts for governance and must not create
a second source of truth without measured evidence that synchronous reads miss
the declared budget.

## Decision

Do **not** introduce an Admin event read model or replicated account projection
now. Keep Auth, App, Submission, and Notification as the authoritative owners;
Admin consumes their bounded read contracts and reports `OK`, `PARTIAL`, or
`OWNER_QUERY_UNAVAILABLE` according to the existing response contracts.

Reopen this decision only after repository/disposable measurements show a core
use case repeatedly exceeds its P3 budget under representative load, or a
freshness/availability requirement cannot be met by the bounded synchronous
seam. Any proposal must include an owner event contract, version/tombstone and
delete semantics, replay/DLQ repair, freshness bounds, backfill, and rollback.

## Conditional Reopen Gate (P4-ADMIN-004)

This decision reopens only when **all** of the following gates pass. No event read model
task is active without every trigger being met. The gate is evaluated by the
`GATE-ADMIN-DEEP-MODULE` review panel.

### Triggers (any one requires **all** sub-conditions)

1. **Budget overrun** (P4-ADMIN-001 budgets):
   - A core Admin read use case (`I-DASH-STATS`, `I-USER-LIST`, `I-SUBMISSION-LIST`) exceeds its
     declared RPC budget (calls or latency) under representative load for ≥3 consecutive
     measurement windows.
   - Evidence: `AdminUseCaseMetrics` histograms show P95 > budget threshold.
   - Mitigation exhausted: bounded executor tuning, batching, and caching already applied.

2. **Repeated provider failure** (P4-ADMIN-003 typed degradation):
   - `OWNER_QUERY_UNAVAILABLE` rate > 5% for the same owner+use-case pair, averaged over 24h,
     sustained for ≥7 days.
   - Evidence: degradation status logs and `AdminWebExceptionHandler` 503 counts.

3. **Freshness/availability requirement unmet**:
   - A documented consumer requirement (e.g., dashboard SLA) cannot be met by the bounded
     synchronous seam because the owner cannot guarantee the required freshness or
     availability tier.

### Gate checklist

| criterion | required evidence |
|---|---|
| Budget overrun confirmed | `AdminUseCaseMetrics` P95/P99 > budget; reproducible under load |
| Mitigation exhausted | Record of bounded executor, batching, caching changes applied |
| Provider failure sustained | 24h+ rate > 5% for 7+ days; degradation logs |
| Freshness/availability requirement documented | Consumer-facing SLA that sync reads cannot meet |
| Proposal includes owner event contract | Draft contract with namespace, version, tombstone, and delete semantics |
| Replay/DLQ repair defined | Dead-letter queue design and replay procedure |
| Freshness bounds specified | Max staleness threshold and enforcement mechanism |
| Backfill procedure defined | One-time replay from owner event stream |
| Rollback procedure defined | Reversion to synchronous reads; projection decommission |
| Source of truth remains owner | Written confirmation that owner writes and recovery are authoritative |

### Decision rule

The gate is **reject-by-default**. A proposal to introduce an Admin event read model
is approved only when all checklist rows are satisfied and the `GATE-ADMIN-DEEP-MODULE`
panel signs off. If any trigger is absent, the synchronous seam remains canonical and
no projection implementation begins.

## Alternatives considered

### Admin event read model

- **Pros**: decouples dashboard reads from owner availability and can reduce
  repeated RPC fan-out.
- **Cons**: duplicates owner facts, adds event ordering and repair semantics,
  introduces freshness lag, and creates another data lifecycle to operate.
- **Why not**: no measured budget failure remains after the bounded aggregate
  and enrichment changes; the duplication cost is currently speculative.

### Keep the former page scan

- **Pros**: no new contract or implementation.
- **Cons**: RPC count grows with account volume and violates the Admin budget.
- **Why not**: the scan was the identified P0 coupling defect and is removed.

## Consequences

- Admin remains dependent on bounded owner RPC availability for required reads.
- Metrics and typed degradation provide the evidence needed for a later,
  evidence-driven decision instead of silently adding replication.
- If the reopen trigger fires, the event model must be additive and derived;
  owner writes and owner recovery remain authoritative.
