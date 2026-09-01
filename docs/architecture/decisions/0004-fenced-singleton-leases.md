# ADR-0004：Fenced singleton leases

- Status: Accepted
- Date: 2026-08-31

## Decision

真正的 singleton job 使用 Admin-owned `fenced_job_leases`：以 `CURRENT_TIMESTAMP(3)` 原子 acquire/renew/release，过期或释放后单调递增 `fence_token`，完成写入要求 owner、token 和未过期条件。`admin:reconciliation`、`admin:scheduled-backup`、`admin:owner-migration` 和 `admin:owner-backup` 使用独立 lease name；同机 `flock` 只作快速门禁。

Submission outbox、Judge recovery 和其他 item-level workers 不使用全局 lease；它们已有 row claim/generation/attempt CAS，保持并发粒度。

## Consequences

暂停、崩溃、时钟偏差、网络分区和 stale completion fail closed。lease rows 是临时控制状态，不进入业务 backup checksum/restore。生产 grant、off-host restore 和部署 authority 仍需外部验证。

详细 scheduler/drain 说明：[`../../operations/monitoring.md`](../../operations/monitoring.md)、[`../../../services/docs/FENCED_LEASE_RUNBOOK.md`](../../../services/docs/FENCED_LEASE_RUNBOOK.md)。
