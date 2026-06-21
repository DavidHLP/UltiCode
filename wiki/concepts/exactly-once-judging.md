---
title: Exactly-Once Judging
type: concept
tags: [judging, idempotency, core]
status: living
updated: 2026-06-21
sources:
  - backend-spring/src/main/java/com/ulticode/modules/queue/outbox/
  - backend-spring/src/main/java/com/ulticode/modules/submission/fence/
  - init-db/migrations/V20260613100000__Create_Judge_Outbox.sql
  - init-db/migrations/V20260613110000__Add_Submission_Generation_And_Lease.sql
aliases: [判题幂等]
---

# Exactly-Once Judging

## The problem
A submission can be retried (flaky client) and a judge worker can crash mid-run.
Naive designs either lose jobs (enqueue without persistence) or judge twice
(retry hits the worker again). Both corrupt standings and user trust.

## The decision
Three mechanisms compose into exactly-once judging:

1. **Transactional outbox** — the `judge_outbox` row is written *in the same DB
   transaction* as the submission. A dispatcher ships it to Redis Streams; a
   shadow comparator reconciles outbox vs stream. No lost jobs.
2. **Generation fence** — each submission carries a monotonic *generation*. Only
   the latest generation's verdict lands; a stale retry's result is discarded.
   No double judging.
3. **Lease + reaper** — judging holds a lease; `JudgingLeaseReaper` and
   `UnackedStreamEntriesReaper` reclaim abandoned work so nothing sticks in
   `JUDGING` forever.

## Where it lives
- `queue/outbox/` (dispatcher, shadow comparator, reaper) · `queue/port/` (hexagonal).
- `submission/fence/SubmissionStateMachine`, `submission/fence/LeaseConstants`,
  `submission/reaper/JudgingLeaseReaper`.
- Migrations `V20260613100000` (outbox) + `V20260613110000` (generation + lease).

## Trade-offs
- An extra table (`judge_outbox`) + a dispatcher loop — accepted for durability.
- A fence column on `submissions` — accepted for correctness.
- At-least-once *delivery* is retained; exactly-once is achieved at the
  *application* layer (fence + ledger), not the transport.

## Related
[[entities/submission]] · [[entities/judge-queue]] · [[entities/sandbox]] ·
[[concepts/notification-idempotency]] · [[overview/judging-pipeline-overview]]
