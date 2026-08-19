# Services autonomy and contract convergence coverage

| Objective | Task | Evidence | Status |
|---|---|---|---|
| Submission immutable facts and no synchronous App/Auth hop | TASK-001 | `SubmissionFactsSnapshotTest.log`, API/provider tests | PASS |
| Submission local transaction/outbox/fence/reaper | TASK-002 | `submission-focused.log` | PASS locally |
| Owner schema/table grant isolation | TASK-003 | `owner-isolation.log` | Local rehearsal; ARCH-002 blocked |
| Search/Notification/Judge event reliability and convergence | TASK-005 | `workers-events.log` | PASS locally; Judge integration skips disclosed |
| Worker rebuild and delete convergence | TASK-006 | TEST-TARGET Judge Redis 4/4; Search E2E 3/3; Notification 92/92; replay ledger | BLOCKED: real MeiliSearch unavailable; Search uses HTTP stub |
| Dual-track compatibility retirement | TASK-007 | `.local/evidence/20260819T2000Z/TASK-007/compatibility-gate.yaml` | PARTIAL/BLOCKED: all inventory paths have explicit gate fields; production retirement evidence absent |
| Final review and delivery | TASK-008 | `reactor-compile.log`, `graphify-update.log`, focused logs | BLOCKED by external gates |

Evidence root: `.local/evidence/20260819T2000Z/`.

External gates are not represented as local success: ARCH-002 requires target authority and production grant sign-off; ARCH-003 requires production stability, deployment authority, all-writer quiesce, observation, rollback and retirement evidence.
