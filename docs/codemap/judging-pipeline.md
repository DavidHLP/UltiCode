---
title: 判题端到端链路
tags: [mirror, architecture, judging]
status: living
updated: 2026-06-21
owner: judging
---

<!-- mirror: 手写 -->

# 判题端到端链路

> 一道提交从「用户点运行/提交」到「前端看到 verdict」的完整路径。幂等机制见 [[exactly-once-judging]]，各段实体见 [[submission]] / [[judge-queue]] / [[sandbox-d-form]]。

## 链路

```
[1] 用户提交代码
    └─ ProblemSubmissionController / SubmissionController  (modules/submission/controller)
        └─ SubmissionService 创建 Submission(status=PENDING)  +  写 judge_outbox(state=PENDING)
                                                                          │
[2] 入队派发                                                                │
    └─ JudgeOutboxDispatcher 扫 PENDING ──► JudgeQueue.enqueue(submissionId, generation)
              （UNIQUE(submissionId,generation) 物理幂等）                   │
              └─ RedissonStreamsJudgeQueueAdapter ──► Redis Streams          │
                                                                              │
[3] Worker 消费  (JudgeWorkerProcessor @Scheduled, 双路径 legacy + port)    │
    └─ poll ──► JudgeJob                                                     │
        └─ fence claim:  generation CAS + 占 lease (TTL 60s, 心跳 20s)       │
            ├─ SubmissionStateMachine.canSystemTransition(PENDING→JUDGING) ✓ │
            └─ status = JUDGING, current_attempt_id, judging_lease_expires_at│
                                                                              │
[4] 沙箱执行  (submission/sandbox)                                          │
    └─ SandboxExecutorImpl 挂载 /job/Solution.* + input.json                 │
        └─ LanguageProfile.{C|Cpp|Java|JS} 选编译/运行命令                    │
            └─ docker run ulticode-sandbox:latest → harness → stdout envelope│
                （exit 0=envelope, 2=panic; seccomp + uid 1000 隔离）         │
                                                                              │
[5] 判决写回                                                                  │
    └─ SubmissionStatusCodec: envelope verdict ──► SubmissionStatus          │
        └─ generation CAS 写终态 (ACCEPTED / WRONG_ANSWER / TLE / RE / ...)  │
            └─ SubmissionStateMachine 终态校验                                │
                                                                              │
[6] 副作用 (解耦, 异步)                                                      │
    └─ SubmissionJudgedEvent                                                 │
        ├─ ContestScoringService / RankingService  (比赛排名, 见 [[contest]])│
        ├─ AchievementService                                                     │
        └─ Notification  (见 [[notification-idempotency]])                        │
```

## 失败/恢复路径

- **worker 崩溃**：lease 不再续租 → `JudgingLeaseReaper`（5s 扫 `idx_lease_expiry`）把 JUDGING 回退 PENDING（唯一合法 `JUDGING→PENDING`）→ 重新入队。
- **Redis Streams 未 ack**：`UnackedStreamEntriesReaper` 回收。
- **沙箱 panic / infra 错**：映射 `SANDBOX_ERROR` / `SYSTEM_ERROR`（`TERMINAL_INFRA`，非用户锅）。
- **重复消息**：outbox UNIQUE + generation fence CAS 双重挡住，迟到的旧 generation 写不进终态。

## 新旧路径（shadow mode）

派发分 legacy（`QueueService.enqueueJudgeJob`）与新 port（`JudgeQueue`）。切换走 M3a/M3b/M3c shadow mode + `OutboxShadowComparator` 比对，见 [[shadow-mode-cutover]]。

## 关联

- 幂等总览 → [[exactly-once-judging]]
- 实体 → [[submission]]、[[judge-queue]]、[[sandbox-d-form]]
- 决策 → [[0001-judge-outbox-and-generation-fencing]]、[[0002-sandbox-d-form-hexagonal]]
