# Scheduler Executor Runbook

Task: \`P3-SCHED-001\`.

Critical scheduled work is explicitly assigned to bounded, owner-local
\`ThreadPoolTaskScheduler\` beans. Each bean has a default pool size of 1 (Admin
audit uses 2 for its two independent pollers), a maximum configured size of 16,
30-second graceful termination, and Micrometer executor active/queued/completed
metrics. A rejected task is an error; it must not be replaced by an unbounded
executor.

## Scheduler map

| Owner | Scheduler | Work | Default pool |
|---|---|---|---:|
| Admin | \`adminAuditScheduler\` | Admin-Audit inbox staging and Admin audit outbox dispatch | 2 |
| Admin | \`adminReconciliationScheduler\` | owner-facts reconciliation and orphan scan | 1 |
| Admin | \`adminBackupScheduler\` | scheduled backup request | 1 |
| Submission | \`submissionJudgeOutboxScheduler\` | Judge outbox claim/dispatch | 1 |
| Submission | \`submissionResultOutboxScheduler\` | result outbox publish | 1 |
| Submission | \`submissionCreatedOutboxScheduler\` | SubmissionCreated publish | 1 |
| Submission | \`submissionLeaseRecoveryScheduler\` | expired judging lease recovery | 1 |
| Search | \`searchConsumeScheduler\` | Redis Streams PEL/new-entry consumption | 1 |
| Search | \`searchHeartbeatScheduler\` | Redis + MeiliSearch readiness heartbeat | 1 |

Pool sizes are deliberately small because each task is bounded by its batch
size and interval. Increase a named pool only after queue/active/rejected
metrics show sustained saturation; never increase a pool to hide a blocked
database, Redis, RPC, or MeiliSearch dependency.

## Signals and response

Micrometer executor metrics are emitted with \`scheduler=<bean purpose>\` and
the \`ulticode.scheduler\` meter name. Inspect:

\`\`\`text
executor.active{scheduler="..."}
executor.queued{scheduler="..."}
executor.pool.size{scheduler="..."}
executor.completed{scheduler="..."}
\`\`\`

For a blocked task, compare the affected scheduler with an independent one:

1. If the heartbeat progresses while consume is blocked, keep the heartbeat
   running and repair the downstream dependency.
2. If recovery progresses while backup/reconciliation is blocked, do not stop
   recovery to clear the maintenance queue.
3. If \`executor.queued\` or rejected-task counters grow, capture thread dumps,
   dependency latency, and the active trace before changing pool size.
4. A scheduler failure is not a reason to bypass owner leases, Redis ACL, RPC
   policy, or migration gates.

## Shutdown

On \`SIGTERM\`, Spring stops accepting new scheduled tasks, waits up to 30 seconds
for bounded work, and cancels periodic tasks that have not started. The queue
consumer leaves unacknowledged records in the Redis PEL for its existing reclaim
path; Submission lease recovery and outbox transactions retain their existing
CAS/claim semantics. Verify the final \`executor.completed\` count and worker
\`last_success\`/PEL metrics after restart.

## Validation

\`\`\`bash
./scripts/test/scheduler-contract.sh
\`\`\`

The contract statically checks every affected \`@Scheduled\` binding and runs a
real Admin test where a blocked backup executor does not starve reconciliation,
closed schedulers reject new work, and executor metrics are registered.
