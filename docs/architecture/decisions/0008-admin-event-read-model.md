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
