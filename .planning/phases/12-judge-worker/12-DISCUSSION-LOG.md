# Phase 12: Judge Worker - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-04-18
**Phase:** 12-judge-worker
**Mode:** Auto (all decisions auto-selected with recommended defaults)
**Areas discussed:** Worker Architecture, Language Fix, Memory Measurement, Test Case Execution, Result Notification, Retry & Error Handling, Verdict Logic

---

## Worker Architecture

| Option | Description | Selected |
|--------|-------------|----------|
| @Scheduled polling | Spring scheduled task polls Redis queue at configurable interval (aligns with existing QueueConfig.pollInterval=1000ms) | Yes |
| Spring ApplicationListener | Event-driven, reactive to queue events | No |
| Dedicated thread with Redisson listener | Redisson RQueue listener pattern | No |

**Auto-selected:** @Scheduled polling — aligns with existing QueueConfig.pollInterval, simplest to implement, already has configurable concurrency via maxConcurrentJobs

---

## Language Fix

| Option | Description | Selected |
|--------|-------------|----------|
| Restrict backend validation to 5 | Fix SubmissionServiceImpl's hardcoded 13-language list to match CodeExecutionService's 5 | Yes |
| Add 8 more languages to sandbox | Build Docker images and wrappers for all 13 languages | No |
| Dynamic validation from config | Load supported languages from application.yml | No |

**Auto-selected:** Restrict backend to 5 — frontend already uses problem_languages table, the mismatch is only in backend validation

---

## Memory Measurement

| Option | Description | Selected |
|--------|-------------|----------|
| cgroup v2 stats | Read /sys/fs/cgroup/memory.current inside container — modern Docker default | Yes |
| /usr/bin/time wrapper | GNU time reports peak RSS — requires tool in container | No |
| Docker stats API | Query Docker daemon for container memory stats — requires Docker socket access | No |

**Auto-selected:** cgroup v2 stats — modern approach, no extra tools needed in container, accurate per-execution measurement

---

## Test Case Execution

| Option | Description | Selected |
|--------|-------------|----------|
| Reuse CodeExecutionService.executeBatch() | Existing batch mode: compile-once, run-many with per-case timeout | Yes |
| New dedicated execution pipeline | Separate execution logic for judge worker | No |
| Sequential single-case execution | One Docker container per test case | No |

**Auto-selected:** Reuse executeBatch() — already implemented, tested, and supports all 5 languages with proper wrapper scripts

---

## Result Notification

| Option | Description | Selected |
|--------|-------------|----------|
| Trigger existing WebSocket submission_result | Use SimpMessagingTemplate to push to /user/{userId}/queue/submission | Yes |
| Frontend polling only | Keep existing polling, no push | No |
| SSE (Server-Sent Events) | New SSE endpoint for real-time updates | No |

**Auto-selected:** Trigger existing WebSocket — frontend already listens for submission_result event, infrastructure is complete

---

## Retry & Error Handling

| Option | Description | Selected |
|--------|-------------|----------|
| maxRetries=3 + exponential backoff | Use existing JudgeJob.maxRetries field, implement 2s→4s→8s backoff | Yes |
| No retry, fail immediately | Single attempt, mark System Error on failure | No |
| Fixed interval retry | Retry 3 times at fixed 5s intervals | No |

**Auto-selected:** Exponential backoff — robust without overwhelming the queue; compile errors skip retry (deterministic failure)

---

## Verdict Logic

| Option | Description | Selected |
|--------|-------------|----------|
| First-fail determines verdict | Priority: RE > MLE > TLE > WA > PE > Accepted; runtime/memory = max across cases | Yes |
| All-cases-aggregate | Compute overall verdict from all test case results combined | No |

**Auto-selected:** First-fail determines verdict — standard competitive programming judge behavior, matches LeetCode/Codeforces pattern

---

## Claude's Discretion

- Exact @Scheduled parameters (initial delay, fixed delay tuning)
- Logger levels and structured log format
- Whether to use @Async for execution within worker
- Unit test structure and mock boundaries

## Deferred Ideas

None — all discussion stayed within phase scope.
