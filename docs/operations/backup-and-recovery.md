# 备份与恢复

## 责任与范围

完整 Owner 备份由外部 Ops runbook 承接，不扩展 Admin HTTP backup API 为跨 Owner 业务接口。归档范围是 `ulticode` control schema 与五个 Owner schema：`auth`、`admin`、`app`、`notification`、`submission`。

`scripts/runbooks/owner-backup-restore.sh` 生成 OpenSSL 加密归档、secret-free manifest、dump SHA-256、表 rows/checksum 和 Flyway history metadata；`restore-drill` 只恢复到一次性 MySQL 目标，运行 migration validate、checksum reconciliation、schema/query smoke，并记录 measured RPO/RTO。密钥由 operator 提供（至少 32 字节），不进入 Git 或日志。

## 并发、保留与恢复

backup、restore-drill、prune 使用同一 fenced database lease（`admin:owner-backup`）并保留同机 `flock` 快速门禁。`admin.fenced_job_leases` 是临时控制状态，不计入业务 checksum 或恢复状态。Retention 只能删除匹配的归档/manifest 对。

恢复顺序：

1. 确认目标是 disposable/授权环境，保存 source commit、schema checksum、manifest 和密钥引用。
2. 解密并校验归档完整性、manifest、dump hash、Owner/schema/table 清单。
3. 按 Owner migration history 做 validate，恢复 control/Owner 数据并做 row/checksum reconciliation。
4. 运行查询、服务 readiness、队列/Inbox 和关键 API smoke；确认不会把派生 Search 索引当作业务备份。
5. 若恢复 MeiliSearch，清理 `search:doc-version:{index}` 后按 Search backfill 重建，并观察索引计数与版本单调性。

## 失败与回滚

错误密钥、缺文件、checksum mismatch、schema mismatch、lease busy、恢复目标不安全或 smoke 失败必须非零退出。生产部署 rollback 使用已验证 descriptor 和 schema-compatible artifact，不通过备份脚本做 schema downgrade。真实 off-host 存储、密钥托管、保留策略和生产 restore authority 仍是外部门禁。

## 参考

- [`../../scripts/runbooks/owner-backup-restore.sh`](../../scripts/runbooks/owner-backup-restore.sh)
- [`../../services/docs/FENCED_LEASE_RUNBOOK.md`](../../services/docs/FENCED_LEASE_RUNBOOK.md)
- [`database-migrations.md`](database-migrations.md)
- [`deployment.md`](deployment.md)
