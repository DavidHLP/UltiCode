# ADR-0002：Submission 单写者与 Owner cutover

- Status: Accepted
- Date: 2026-08-30

## Context

App-local writer、Admin rejudge fallback 和 Submission owner writer 曾使 mutation、fence、outbox 与 rejudge 责任分裂；一次请求无法可靠判断哪一方是权威。

## Decision

`backend-submission` 是 Submission intake、verdict、generation/attempt fence、rejudge、judge/result/created outbox 的唯一持久化 Owner。App 只在 request boundary 组装不可变 `SubmissionFactsSnapshot` 并调用 remote contract；Admin 只发带 delegation/trace/idempotency 的 rejudge command；Judge 通过 owner fence/verdict contract 回写。普通读取使用 bounded owner facts，local projection 仅作为显式 `legacy-rollback` seam。

## Consequences

- writer、receipt、generation CAS 和恢复语义集中在一个 Owner。
- backfill 采用 dry-run、批量 checkpoint、insert-only、NULL-safe conflict detection、checksum/parity 后再 contraction。
- 生产 traffic drain、真实 schema cutover 和 external N-1 consumer evidence 仍需部署 authority；repository/disposable proof 不替代它们。

详细任务和证据见 [`../../archive/architecture-remediation-2026-08/remediation-traceability.md`](../../archive/architecture-remediation-2026-08/remediation-traceability.md) 与 [`../../project/history/architecture-remediation-20260830.md`](../../project/history/architecture-remediation-20260830.md)。
