---
title: Submission
type: entity
tags: [judging, core]
status: living
updated: 2026-06-21
sources:
  - backend-spring/src/main/java/com/ulticode/modules/submission/
  - init-db/migrations/V20260613110000__Add_Submission_Generation_And_Lease.sql
aliases: [提交]
---

# Submission

The central entity of the judge: one user's code for one problem, driven through
a state machine to a verdict. Its correctness rests on a **generation fence** and
a **lease** so retries and crashes can't produce double judging.

> Pipeline context: [[overview/judging-pipeline-overview]]. Invariant rationale:
> [[concepts/exactly-once-judging]].

## Responsibility

Owns submission creation, the judging state machine, sandbox invocation, verdict
persistence, and the `SubmissionJudgedEvent`. The enqueue itself is delegated to
the [[entities/judge-queue]] module.

## Key tables

- `submissions` — code, language, status, problem/user ref, **generation**, lease columns.
- (judging uses `judge_outbox` from the `queue` module — see [[entities/judge-queue]].)

## Key flows

```
create → IN_QUEUED ──(fence: stamp generation)──► outbox enqueues ──►
   queue worker takes lease → JUDGING ──(sandbox runs cases)──►
   JUDGED ──► SubmissionJudgedEvent ──► notification + standings
```

- **Create**: `ProblemSubmissionController` → `SubmissionService` writes the row
  `IN_QUEUED` with a fresh **generation** in the same transaction as the outbox row.
- **State machine**: `submission/fence/SubmissionStateMachine` advances
  `IN_QUEUED → JUDGING → JUDGED` (and terminal error states). `SubmissionStatus` enum.
- **Lease**: while judging, the worker holds a lease (`fence/LeaseConstants`);
  `submission/reaper/JudgingLeaseReaper` reclaims abandoned leases.
- **Execute**: `submission/sandbox/executor/SandboxExecutorImpl` runs each case via
  a `*LanguageProfile` (C/Cpp/Java/JavaScript/Python) against the [[entities/sandbox]].
  `CaseScope` enum controls which cases run (sample/full).
- **Codec**: `submission/codec/SubmissionStatusCodec` handles test-detail JSON.
- **Emit**: `submission/event/SubmissionJudgedEvent` fans out to `notification`
  and (in a contest) `contest`.

## Key classes

| Class | Role |
|-------|------|
| `SubmissionStateMachine` | legal status transitions + generation check |
| `JudgingLeaseReaper` | reclaims stuck `JUDGING` rows |
| `SandboxExecutorImpl` | drives the Docker sandbox per language |
| `{C,Cpp,Java,JavaScript,Python}LanguageProfile` | per-lang compile/run/normalize |
| `SubmissionStatusCodec` | test-detail JSON (de)serialization |
| `CodeExecutionService` / `CodeExecutionHelper` | run-without-judge (IDE run) |

## Source files

- `backend-spring/.../modules/submission/` (controller, service, fence, sandbox, reaper, event, dto, enums).
- `init-db/migrations/V20260613110000__Add_Submission_Generation_And_Lease.sql` — generation + lease.

## Cross-links

- [[entities/judge-queue]] · [[entities/sandbox]] · [[entities/contest]]
- [[concepts/exactly-once-judging]] · [[concepts/notification-idempotency]]
- [[overview/judging-pipeline-overview]]

## Gotchas

- A retried create must **not** double-judge: the generation fence discards the
  older run. Never bypass `SubmissionStateMachine` to set status directly.
- `JudgingLeaseReaper` and the queue's `UnackedStreamEntriesReaper` must both run,
  or a crashed worker leaves submissions in `JUDGING` forever.
- Run-without-judge (`CodeExecutionService`) shares the sandbox but **not** the
  state machine — it's a separate path.
