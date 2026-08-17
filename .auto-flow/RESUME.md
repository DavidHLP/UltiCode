# Resume

- Active task: CONTRACT-007 remains blocked at the authorized Submission single-hop/single-writer cutover; CONTRACT-002 through CONTRACT-006 source-boundary and codec/catalog work is review/validation complete; SPLIT-005 final Phase Gate remains an independent in-progress historical stage
- Branch: main @ dc32f114e（工作区保留既有验证用测试修复；不 commit）
- Objective: 将 backend-app-api 收敛为 App-owned contract seam，建立 backend-submission-api/backend-notification-api，迁移全部 callers/providers，并在授权 cutover 后删除 Submission compat 两跳代理与重复 codec/status catalog
- Dependencies: CONTRACT-001(done) → CONTRACT-001-COMMON(done) → {CONTRACT-002, CONTRACT-003}(done) → {CONTRACT-004, CONTRACT-005}(done) → CONTRACT-006(done) → CONTRACT-007 authorized cutover → CONTRACT-008 final gate; SPLIT-004/SPLIT-005 and existing SEARCH/SPLIT history remain prerequisite evidence
- Completed: SPLIT-001/002/003/004、SEARCH-001/002/003、SPLIT-005-env-quick、CONTRACT-001 owner/release matrix、CONTRACT-001-COMMON common extraction、CONTRACT-002/003 API artifacts、CONTRACT-004/005 caller/provider migration、CONTRACT-006 canonical codec/catalog
- In progress: SPLIT-005 final gate（sandbox/Testcontainers evidence 与 retirement authority 未闭合）
- Next ready: none — CONTRACT-007 is blocked on explicit release/cutover authority and the existing SPLIT-005 sandbox/Testcontainers evidence; CONTRACT-008 waits for that gate
- Environment: Docker 可用但无外网；既有 ulticode-mysql 持久卷未触碰；disposable verification resources 已清理；`.auto-flow/` 不 commit
- Protected boundaries: 不编辑 applied migration；不执行生产 route/grant/REVOKE；不删除 mysql_data；不新增 Entity/Mapper shared module、broker 或永久兼容 alias；保留 App/Auth/Contest ownership
- Evidence: `.auto-flow/CONTRACT-001-OWNER-MATRIX.md` 完成 219 个顶层类型与 66 个 nested 类型的 owner、例外、模块包名、Dubbo group/version、matched release 和 DEC-011 盘点；common extraction 新增八个 common types；Submission/Notification API artifacts、调用方/POM/provider references、canonical DTO codec/catalog 已迁移；affected Maven tests、28 个 Submission IT 与 direct negative scans pass；graphify updated to 26,513 nodes/77,128 edges
- Rollback: contract package/source 回到上一 verified artifact；runtime cutover 只走 route/grant/watermark/reconciliation runbook，compat 删除失败先回滚 artifact，不改 migration
