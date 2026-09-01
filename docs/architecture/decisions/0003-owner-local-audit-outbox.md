# ADR-0003：Owner-local audit outbox

- Status: Accepted
- Date: 2026-08-31

## Context

Auth 和 App 曾直接写 Admin schema 的 `audit_outbox`，形成跨 Owner 数据库 grant，并把 claim/dispatch 状态放在错误的 Owner。

## Decision

Auth/App 在各自业务事务内写 local audit outbox；dispatcher 发布版本化 `AuditRecorded` 到 `stream:integration`。Admin 只通过固定 `Admin-Audit` consumer inbox 接收合法事件，并按 event id 幂等写 `audit_logs`。旧跨 schema INSERT grant 在所有 local outbox 已建立后由 post-owner migration 撤销。

## Consequences

Redis/XADD、重复、乱序、坏事件和 handler failure 由 claim fencing、Inbox dedup、lease/retry/DLQ 处理；actor 必须来自 authenticated/delegated principal。审计最终一致，不把 ThreadLocal 或跨数据库事务当作可靠性机制。

详细运行手册见 [`../../operations/incident-response.md`](../../operations/incident-response.md) 与归档 remediation traceability。
