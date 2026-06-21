---
title: Judging Pipeline Overview
type: overview
tags: [judging, pipeline, core]
status: living
updated: 2026-06-21
sources:
  - backend-spring/src/main/java/com/ulticode/modules/submission/
  - backend-spring/src/main/java/com/ulticode/modules/queue/
  - docker/sandbox/
  - init-db/migrations/V20260613100000__Create_Judge_Outbox.sql
  - init-db/migrations/V20260613110000__Add_Submission_Generation_And_Lease.sql
---

# Judging Pipeline Overview

The path a submission travels from keystroke to verdict. This is UltiCode's
central pipeline and the place three correctness invariants meet:
**exactly-once delivery**, **generation fencing**, and **lease-protected judging**.
Deep rationale in [[concepts/exactly-once-judging]].

## End-to-end

```
 console/                  backend-spring/                     sandbox
 POST /problems/{id}/      modules: submission → queue          docker image
 submissions               ────────────────────────────         (per submit)
   │                         │            │           │            │
   │  ① create Submission    │            │           │            │
   ├────────────────────────►│ IN_QUEUED  │           │            │
   │                         │  (gen fence)│           │            │
   │                         ├──────────► (outbox row) │            │
   │                         │            │ dispatch  │            │
   │                         │            ▼           │            │
   │                         │      Redis Streams ────┐            │
   │                         │            │           │ worker     │
   │                         │            │           ▼            │
   │                         │            │    JudgeWorkerProcessor│
   │                         │            │           │  lease     │
   │                         │            │           ├──────────►│ run cases
   │                         │            │           │  ◄────────│ verdict
   │                         │  ② SubmissionStateMachine           │
   │                         │     JUDGING → JUDGED                 │
   │                         │           │                          │
   │                         │     SubmissionJudgedEvent            │
   │                         │           │                          │
   │                         │     ③ notification intent            │
   │                         │           │                          │
   │                         │     ④ websocket push ──────────────► console tab
   │  ⑤ poll/SSE/ws verdict  │                                      │
   │◄────────────────────────┘                                      │
```

## The five stages

1. **Create** — `submission/controller/ProblemSubmissionController` accepts the
   code, `submission` writes a `Submission` row in `IN_QUEUED` and stamps a
   **generation** (fence token). The same request may retry; the generation fence
   ensures only one judging run lands per logical submission — see
   [[entities/submission]].

2. **Enqueue (outbox → stream)** — instead of pushing straight to Redis Streams
   (which can lose a job if the write and the enqueue aren't atomic), the
   `queue/outbox` writes a `judge_outbox` row **in the same DB transaction** as
   the submission. `JudgeOutboxDispatcher` then ships it to a Redis Stream;
   `OutboxShadowComparator` reconciles outbox vs stream; see
   [[entities/judge-queue]].

3. **Worker pulls + judges** — `queue/processor/JudgeWorkerProcessor` consumes
   the stream, takes a **lease** on the submission (`submission/fence/` +
   `JudgingLeaseReaper`), and runs each test case in the sandbox via
   `submission/sandbox/executor/SandboxExecutorImpl` + per-language
   `*LanguageProfile` (C/Cpp/Java/JavaScript/Python). See [[entities/sandbox]].

4. **Verdict written + event** — `SubmissionStateMachine` advances
   `IN_QUEUED → JUDGING → JUDGED`; a `SubmissionJudgedEvent` is published.

5. **Notify** — `notification` records an intent and delivers through the
   **delivery ledger** (at-least-once → exactly-once via ledger dedup);
   `websocket` pushes to the user's open tab. See [[entities/notification]] and
   [[concepts/notification-idempotency]].

## The three invariants (and where they live)

| Invariant | Mechanism | Source |
|-----------|-----------|--------|
| **Exactly-once judging** | outbox row + Redis Stream + shadow comparator + generation fence | `queue/outbox/*`, `submission/fence/*`, `V20260613100000`, `V20260613110000` |
| **No stuck jobs** | lease on the submission + `JudgingLeaseReaper` + stream `UnackedStreamEntriesReaper` | `submission/reaper/`, `queue/outbox/reaper/` |
| **Portable queue** | `queue/port/JudgeQueue` interface with `RedissonStreamsJudgeQueueAdapter` (prod) + `InMemoryJudgeQueueAdapter` (tests) | `queue/port/` |

## Adapters (hexagonal)

The queue is behind a port so tests don't need Redis:
- `queue/port/JudgeQueue.java` — the port.
- `queue/port/adapter/RedissonStreamsJudgeQueueAdapter.java` — production.
- `queue/port/adapter/InMemoryJudgeQueueAdapter.java` — tests.

The sandbox is similarly ported: `submission/sandbox/SandboxExecutor` with
`SandboxExecutorImpl` (Docker) and `InMemorySandboxAdapter`.

## Contest path

If the submission is inside a contest, stage 4 also triggers `contest` to record
`contest_problem_results`, update `first_solve_records` and `global_rankings`,
and recompute standings per the contest's `ScoringRule`. See [[entities/contest]]
and [[concepts/virtual-contest]].

## Reading order

`entities/submission` → `entities/judge-queue` → `entities/sandbox` →
`concepts/exactly-once-judging` → `concepts/notification-idempotency`.
