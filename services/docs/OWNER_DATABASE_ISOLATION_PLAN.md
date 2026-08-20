# Owner 数据库隔离实施说明

更新时间：2026-08-19

当前项目只有一个 TEST-TARGET；用户已授权在该目标完成 Owner 账号、migration、backfill、cutover、观察和 rollback。本说明记录已执行状态，不声称存在 production environment。

## 当前矩阵

| Owner | 连接变量 | 当前默认 | 目标账号/数据库 | 状态 |
| --- | --- | --- | --- | --- |
| Auth | `AUTH_DB_HOST/PORT/NAME/USER/PASSWORD` | `DB_*` 仅兼容 fallback | `auth_rw` / `auth` | TEST-TARGET active |
| Admin | `ADMIN_DB_HOST/PORT/NAME/USER/PASSWORD` | `DB_*` 仅兼容 fallback | `admin_rw` / `admin` | TEST-TARGET active |
| App | `APP_DB_HOST/PORT/NAME/USER/PASSWORD` | `DB_*` 仅兼容 fallback | `app_rw` / `app` | TEST-TARGET active |
| Submission | `SUBMISSION_DB_HOST/PORT/NAME/USER/PASSWORD` | 独立 `submission` 配置 | `submission_rw` / `submission` | TEST-TARGET active |
| Notification | `NOTIFICATION_DB_HOST/PORT/NAME/USER/PASSWORD` | `DB_*` 仅兼容 fallback | `notification_rw` / `notification` | TEST-TARGET active |

当前 `.env` 为唯一部署密管输入；Owner runtime 与 direct-grant migration principal 已分离。独立 instance 不是本项目目标，schema/account/permission isolation 是当前物理边界。

运行时迁移边界：`backend-auth` 的 `spring.flyway.enabled` 继续由
`AUTH_FLYWAY_ENABLED` 控制且默认关闭，runtime 账号不执行 canonical migration。
Owner schema migration 由独立 direct-grant migration principal 串行执行；
TEST-TARGET 的 Auth/Admin/App/Notification/Submission Flyway validate 均通过。

## `users` 职责拆分

目标职责已经区分：Auth 持有 account/credential、authorization/status（id、
username、email、password、refresh/reset、role、permission、active、ban），App
持有 `user_profiles(account_id, ...)` 的 profile 字段。canonical shared history 已执行
`V20260729150000__Create_User_Profiles_Table.sql` 的回填，并由后续
`V20260806120000__Drop_Profile_Columns_From_Users.sql` 完成 shared `users` profile
列的 contract；这两份 applied migration 不得编辑。

Auth owner bootstrap 的兼容 profile 列由后续 Auth-owner contract migration
`V20260820180000__Narrow_Auth_Users_To_Account_Ownership.sql` 收窄；该 migration
不得回写或编辑早期 expand migration。运行时职责已经切开：Auth account/status
写入只进入 `auth.users`，App profile 写入只进入 `app.user_profiles`。App 用户投影
通过 Auth RPC 与 App profile mapper 组合，不再由 App datasource join `users`；
moderation ban 命令携带 actor、trace、idempotency 和 expected authz version 调用
Auth owner。

对尚未完成的 owner schema/runtime 切换，仍按以下顺序执行：

1. **Expand**：为 Auth account/status 与 App profile 建立明确 owner tables、索引和 version/updated-at 字段；在对应 owner history 中保留旧列和旧读路径。
2. **Backfill**：以 `users.id` 为稳定 account id，幂等回填；记录行数、主键 checksum、空值/孤儿引用和重复冲突。
3. **Verify**：Auth、Admin、App、Search、Notification reader/writer 矩阵已转换为 Owner RPC + local profile read，并由 focused tests/静态扫描复核。
4. **Cut over**：TEST-TARGET backfill 在 PM2 writers=0 时执行 idempotent no-op → manifest-scoped rollback → re-backfill；12/12 account/profile rows 和完整 checksums 匹配。
5. **Observe**：登录/account、profile 写读、ban/permission、搜索用户文档、通知收件人和管理查询由后续 ARCH Gate 继续验证。
6. **Contract**：对既有 owner schema 先以 quiesce confirmation 执行
   `owner-user-profile-backfill.sh contract-preflight`，确认 manifest、完整
   account/profile checksum（含 soft-deleted accounts）和 App profile writer，
   再执行 Auth-owner contract migration；禁止编辑 applied migration。

## 权限与回滚

- migration job 使用独立 direct-grant 高权限账号；runtime 使用 owner 专用账号；
- runtime 账号不得拥有 global/schema-wide `ALL`、`GRANT OPTION`、隐式角色继承或未登记的其他 Owner 表 DML；唯一登记例外是 `auth_rw`/`app_rw` 对 `admin.audit_outbox` 的 append-only `INSERT`；
- 每次切换前后保存 rows/checksum/privilege snapshot；
- 失败时先回滚 route/consumer 到上一 artifact，再按 manifest/copy/reconcile runbook 回写；不得 `DROP`、`TRUNCATE` 或重置共享 source；
- 本地 TEST-TARGET/DEV-LOCAL 证据只能证明 rehearsal 与脚本契约；不得替代外部目标 authority、users/profile responsibility sign-off、physical cutover 或 production acceptance。
- ARCH-002 当前 blocked，直到真实目标账号/权限、责任切换、回填/回滚与最终外部 Review/Validation 证据齐全。
- ARCH-003 的 remote stability、deployment authority、all-writer quiesce、observation/rollback 和 compatibility retirement 仍是外部 blocker。
