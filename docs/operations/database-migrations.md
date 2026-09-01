# 数据库迁移与 Owner 收敛

## 唯一真源

`init-db/migrations/` 是唯一 Flyway migration source。命名为 `V{14-digit timestamp}__Description.sql`；已应用 migration 永不编辑，只新增向前 migration。命令、profile、baseline、seed 和低层原语见 [`../../init-db/README.md`](../../init-db/README.md)。

## Owner 顺序与权限

迁移编排固定为：

```text
shared → auth → admin → app → notification → submission → post-owner controls
```

`owner-migrate` / `owner-migration-manifest.sh` 校验 owner schema/location、runtime 与 migration account 分离、依赖顺序、源文件 checksum，并以 fenced lease 防并发。runtime 账号不能拥有其他 Owner 表、global/schema `ALL`、`GRANT OPTION` 或隐式角色继承。

当前逻辑边界：Auth 持 account/credential/RBAC/refresh；App 持 profile 和 OJ/社区聚合；Admin 持治理/审计/设置/backup；Notification 持通知与投递；Submission 持提交与判题 outbox。物理独立实例不是当前前置条件，先完成 schema/account/唯一 writer。

## Expand → backfill → verify → cutover → contract

- **Expand**：新增 owner table、索引、version/updated-at 和 proof；保留兼容读路径。
- **Backfill**：以稳定主键批量、幂等、insert-only 回填；记录 count、checksum、孤儿、冲突和 checkpoint。
- **Verify**：检查 missing/extra keys、NULL-safe fields、checksum、writer 状态、权限和 reader 语义。
- **Cutover**：停止并 drain 所有 writer，显式确认后撤销旧 grants/切换 route；不得顺便做隐式全表复制。
- **Contract**：观察期、备份和 rollback 证据满足后，才删除 legacy columns/tables/contracts。

Backfill 默认 dry-run；同主键字段冲突 fail closed 并导出 TSV，不覆盖较新的 owner 行。Rollback 回到已验证 artifact，先恢复 route/grant，再按 runbook 回写新增行；不 `DROP`、`TRUNCATE` 或编辑历史 migration。

## Users 垂直拆分

Auth 只写 `users` account/status/authorization 字段；App 写 `user_profiles(account_id, ...)`。使用后续 migration 完成回填和 compatibility-column contraction；Search、Admin、Notification 通过 bounded facts/Identity contract，不做跨 Owner SQL join。软删除 account 也必须纳入 checksum 和 reconciliation。

#### Submission read owner cutover 与 schema contraction

正常 App user、contest、Problem-statistics、user-tag、generation 和 Admin Submission reads 使用 `backend-submission` owner facts；App local mapper/projection 仅由显式 `legacy-rollback` 保留。Submission provider 按 `account_id` 分组分页，单页上限 500；`createdSince=null` 为全量，非空为包含式增量窗口。Admin 通过 owner adapter 做 full/incremental reconciliation，验证 ordering、duplicates、nulls、count 和 failures，并以 lease/fence 防多副本重叠。

物理 contraction 与普通 Flyway 分开：先写 `owner_contraction_proof`，再以 backup、quiesce、parity、checksum 和 grant gates 允许 contract migration。当前 repository/disposable rehearsal 已通过；真实 production target、traffic drain、backup authority 和 cutover 仍由部署方执行。App 不再读取 Submission-owned SQL。

## Notification contraction

Notification 是 `notifications`、preferences、delivery ledger 的唯一 writer。App 只发布 intent 并保留 WebSocket relay；Admin 通过 `NotificationReconciliationReadPort` 消费 owner facts。物理迁移使用独立 `notification` schema history；默认 preflight，写入要求 `--execute` 与一次性确认 token。Rollback 先回写目标新增行，再恢复 App grants。

## 备份与证据

每次 owner migration 保存不含 secret 的 manifest、rows/checksum、privilege snapshot、lease/report 和失败 artifact。完整五 Owner 备份、加密、恢复演练、retention 和 measured RPO/RTO 见 [备份与恢复](backup-and-recovery.md)。

## 参考入口

- [`init-db/README.md`](../../init-db/README.md)
- [`scripts/README.md`](../../scripts/README.md)
- [`../../scripts/runbooks/owner-migration-manifest.sh`](../../scripts/runbooks/owner-migration-manifest.sh)
- [`../../scripts/runbooks/owner-schema-contraction.sh`](../../scripts/runbooks/owner-schema-contraction.sh)
- [`../../services/docs/CONTRACT_COMPAT_GATE.md`](../../services/docs/CONTRACT_COMPAT_GATE.md)
