---
title: Judge Queue
type: entity
tags: [judging, queue, core, type/entity]
status: living
updated: 2026-06-21
sources:
  - backend-spring/src/main/java/com/ulticode/modules/queue/
  - init-db/migrations/V20260613100000__Create_Judge_Outbox.sql
aliases: [判题队列]
---

# Judge Queue

The async backbone of judging: submissions become jobs, shipped through a
**transactional outbox** into **Redis Streams**, consumed by workers. Hexagonal —
a port lets tests run without Redis. Reapers reclaim lost work.

> Pipeline: [[overview/judging-pipeline-overview]].

## Responsibility

Owns the outbox table, stream dispatch, the worker processor, and the reaper/
shadow-comparator that keep at-least-once delivery honest.

## Key tables

- `judge_outbox` — the transactional outbox row written with the submission.

## Architecture (hexagonal)

```
Submission tx ──► JudgeOutboxRecord (DB, same tx)
                       │
            JudgeOutboxDispatcher ──► Redis Stream
                       │                  │
            OutboxShadowComparator ◄──┘   (reconcile)
                                          │
                              JudgeWorkerProcessor (consumer)
                                          │ takes lease
                                          ▼
                                    [[entities/submission]] judging
```

## Key classes

| Class | Role |
|-------|------|
| `JudgeQueue` (port) | the interface |
| `RedissonStreamsJudgeQueueAdapter` | production (Redis Streams via Redisson) |
| `InMemoryJudgeQueueAdapter` | tests (no Redis) |
| `JudgeOutboxDispatcher` | ship outbox rows → stream |
| `OutboxShadowComparator` | reconcile outbox vs stream |
| `UnackedStreamEntriesReaper` | reclaim unacked stream entries |
| `JudgeWorkerProcessor` | consume + drive judging |
| `JudgeJob` / `JudgeJobEnvelope` / `JudgeJobHandle` | job payloads |

## Source files

- `backend-spring/.../modules/queue/` (port, adapter, outbox/{dispatcher,reaper,shadow}, processor, job, service, config, constants).

## Cross-links

- [[entities/submission]] · [[entities/sandbox]]
- [[overview/judging-pipeline-overview]]

## Gotchas

- The outbox row and the submission row must be **one transaction** — splitting
  them reintroduces the lost-job window the outbox exists to close.
- Both reapers must run: `UnackedStreamEntriesReaper` (stream side) and
  `JudgingLeaseReaper` (submission side, in `submission`).
- `InMemoryJudgeQueueAdapter` is for fast tests; never let prod wire resolve to it.
