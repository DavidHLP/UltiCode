# Resume

- Active task: CONTRACT-007 is in progress; the CR compatibility seam, owner reaper, routing gates and integration selector are repaired and locally validated, while production remote route and runtime observation remain externally gated
- Branch: main @ 34417d912（工作区保留 CR 修复与可回滚验证修改；不 commit）
- Objective: 将 backend-app-api 收敛为 App-owned contract seam，建立 backend-submission-api/backend-notification-api，迁移全部 callers/providers，并在授权 cutover 后退役 Submission compat 两跳代理与重复 codec/status catalog
- Dependencies: CONTRACT-001(done) → CONTRACT-001-COMMON(done) → {CONTRACT-002, CONTRACT-003}(done) → {CONTRACT-004, CONTRACT-005}(done) → CONTRACT-006(done) → CONTRACT-007 authorized cutover → CONTRACT-008 final gate; SPLIT-004/SPLIT-005 and existing SEARCH/SPLIT history remain prerequisite evidence
- Completed: SPLIT-001/002/003/004、SEARCH-001/002/003、SPLIT-005-env-quick、CONTRACT-001 owner/release matrix、CONTRACT-001-COMMON common extraction、CONTRACT-002/003 API artifacts、CONTRACT-004/005 caller/provider migration、CONTRACT-006 canonical codec/catalog
- In progress: SPLIT-005 final gate（本地 sandbox、真实 MySQL/Redis 与官方 integration 已闭合；生产 route/运行时观察仍未执行）
- Next ready: none — CONTRACT-007 awaits a production release window for `APP_SUBMISSION_ROUTING_MODE=remote` + `APP_SUBMISSION_OWNER_MODE=local` and real single-writer observation; CONTRACT-008 waits for that gate
- Environment: Docker 可用但无外网；既有 ulticode-mysql 持久卷未触碰；disposable verification resources 已清理；`.auto-flow/` 不 commit
- Protected boundaries: 不编辑 applied migration；不执行生产 route/grant/REVOKE；不删除 mysql_data；不新增 Entity/Mapper shared module、broker 或永久兼容 alias；保留 App/Auth/Contest ownership
- Evidence: `.auto-flow/CONTRACT-001-OWNER-MATRIX.md` 完成 219 个顶层类型与 66 个 nested 类型的 owner、例外、模块包名、Dubbo group/version、matched release 和 DEC-011 盘点；common extraction 新增八个 common types；Submission/Notification API artifacts、调用方/POM/provider references、canonical DTO codec/catalog 已迁移；Submission Provider/Reaper 5/0/0/0、真实 MySQL owner IT 4/0/0/0、真实 Redis 4/0/0/0、services-wide integration post-run aggregate 823 reports / 2,720/0/0/24、disposable grant allow→deny→allow 均通过；Graphify 27,577/80,540 已刷新
- Rollback: contract package/source 回到上一 verified artifact；runtime cutover 只走 route/grant/watermark/reconciliation runbook，compat 删除失败先回滚 artifact，不改 migration
