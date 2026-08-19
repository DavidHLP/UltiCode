# Owner 数据库隔离实施说明

更新时间：2026-08-18

这是一份可回滚的 expand/verify 计划，不是生产切库授权。当前代码已为 Auth、Admin、App、Notification 增加独立连接变量；未设置专用变量时仍回落到现有 `DB_*`，以保持本地共享数据库可启动。

## 当前矩阵

| Owner | 连接变量 | 当前默认 | 目标账号/数据库 | 状态 |
| --- | --- | --- | --- | --- |
| Auth | `AUTH_DB_HOST/PORT/NAME/USER/PASSWORD` | `DB_*` | `auth_rw` / `auth` | preparation only |
| Admin | `ADMIN_DB_HOST/PORT/NAME/USER/PASSWORD` | `DB_*` | `admin_rw` / `admin` | preparation only |
| App | `APP_DB_HOST/PORT/NAME/USER/PASSWORD` | `DB_*` | `app_rw` / `app` | preparation only |
| Submission | `SUBMISSION_DB_HOST/PORT/NAME/USER/PASSWORD` | 独立 `submission` 配置 | `submission_rw` / `submission` | 已完成独立配置，仍需运行时权限观察 |
| Notification | `NOTIFICATION_DB_HOST/PORT/NAME/USER/PASSWORD` | `DB_*` | `notification_rw` / `notification` | preparation/cutover tooling exists |

生产 Compose 传递这些变量并保留兼容回落；真正启用隔离时，部署密管必须提供专用值，不能依赖回落。独立实例与独立 database/schema 是两级不同目标：先完成 schema/account/permission isolation，再按备份、SLA 和资源需求切到独立 MySQL instance。

运行时迁移边界：`backend-auth` 的 `spring.flyway.enabled` 默认由
`AUTH_FLYWAY_ENABLED` 控制且默认关闭，运行时账号不得执行 canonical root migration。
Owner schema migration 必须由独立的高权限 migration job 串行执行（例如
`MIGRATION_SCHEMA=auth ./scripts/dev/migrate.sh migrate`），再以 `AUTH_DB_*` 提供给
Auth runtime；这只是 preparation，不能替代账号、权限和数据切换验收。

## `users` 职责拆分

目标职责已经区分：Auth 持有 account/credential、authorization/status（id、
username、email、password、refresh/reset、role、permission、active、ban），App
持有 `user_profiles(account_id, ...)` 的 profile 字段。canonical shared history 已执行
`V20260729150000__Create_User_Profiles_Table.sql` 的回填，并由后续
`V20260806120000__Drop_Profile_Columns_From_Users.sql` 完成 shared `users` profile
列的 contract；这两份 applied migration 不得编辑。

注意 fresh owner bootstrap 仍有历史差异：`init-db/migrations/auth/`
`V20260729140100__Create_Auth_Schema_Tables.sql` 保留兼容 profile 列，直到 owner schema
拥有自己的后续 forward migration。不能把 shared history 已完成的 profile contract
误写成 Auth owner schema、runtime 账号和权限切换已经完成。

对尚未完成的 owner schema/runtime 切换，仍按以下顺序执行：

1. **Expand**：为 Auth account/status 与 App profile 建立明确 owner tables、索引和 version/updated-at 字段；在对应 owner history 中保留旧列和旧读路径。
2. **Backfill**：以 `users.id` 为稳定 account id，幂等回填；记录行数、主键 checksum、空值/孤儿引用和重复冲突。
3. **Verify**：双读或 shadow compare；确认 Auth、Admin、App、Search、Notification 的 reader/writer 矩阵。
4. **Cut over**：按 owner 路由切读写，单 writer；需要 all-writer quiesce、一次性确认和可观测窗口。
5. **Observe**：至少覆盖登录、profile 更新、ban/permission、搜索用户文档、通知收件人和管理查询。
6. **Contract**：在备份、回滚 artifact 和权限负向测试完成后，才讨论撤销旧 grant/列；禁止编辑 applied migration。

## 权限与回滚

- migration job 使用独立高权限账号；runtime 使用 owner 专用账号；
- runtime 账号不得拥有 global/schema-wide `ALL`、`GRANT OPTION`、隐式角色继承或其他 Owner 表 DML；
- 每次切换前后保存 rows/checksum/privilege snapshot；
- 失败时先回滚 route/consumer 到上一 artifact，再按 copy/reconcile runbook 回写数据；不得 `DROP`、`TRUNCATE` 或重置共享数据作为回滚；
- 当前项目没有生产环境，因此本地 disposable MySQL 只能证明 migration/config 行为，不能证明生产实例隔离或生产性能。

## 验收与阻塞

ARCH-002 的完成条件是：四个 Owner 的专用连接变量与账号实际可用、迁移回填可重复、权限负向测试通过、本地回滚演练通过，并明确外部部署窗口。仅修改配置或创建 shadow user 不等于数据库物理隔离完成。
