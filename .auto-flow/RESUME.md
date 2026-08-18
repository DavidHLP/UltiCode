# Resume

- Active task: CONTRACT-007 and CONTRACT-008 are complete; the local Submission direct-owner cutover, compatibility-provider retirement and final contract audit are closed
- Branch: main @ 2f19a7f8e（本轮新增 Submission direct owner provider、删除 compat/App duplicate provider，并有配置、runbook、文档与 `.auto-flow` 未提交差异）
- Objective: 将 backend-app-api 收敛为 App-owned contract seam，建立 backend-submission-api/backend-notification-api，迁移全部 callers/providers，并在授权 cutover 后退役 Submission compat 两跳代理与重复 codec/status catalog
- Dependencies: CONTRACT-001(done) → CONTRACT-001-COMMON(done) → {CONTRACT-002, CONTRACT-003}(done) → {CONTRACT-004, CONTRACT-005}(done) → CONTRACT-006(done) → CONTRACT-007 authorized cutover → CONTRACT-008 final gate; SPLIT-004/SPLIT-005 and existing SEARCH/SPLIT history remain prerequisite evidence
- Completed: SPLIT-001/002/003/004、SEARCH-001/002/003、SPLIT-005-env-quick、CONTRACT-001 owner/release matrix、CONTRACT-001-COMMON common extraction、CONTRACT-002/003 API artifacts、CONTRACT-004/005 caller/provider migration、CONTRACT-006 canonical codec/catalog
- In progress: none within this objective; CONTRACT-007 direct providers and the final CONTRACT-008 Completion/Coverage Audit passed
- Next ready: no dependent Task; preserve the verified local route/grant/watermark/reconciliation rollback path for a future scoped change
- Environment: 本地 Docker/PM2 为唯一目标；MySQL、Redis、Nacos healthy，六个 PM2 服务 online 且 clean restart=0；`submission` owner schema 已复制并与 `ulticode` source rows/checksums 对齐；`.auto-flow/` 控制面记录保留在当前未提交工作区
- Protected boundaries: 不编辑 applied migration；不写入远端/共享环境；不删除 mysql_data；不新增 Entity/Mapper shared module、broker 或永久兼容 alias；保留 App/Auth/Contest ownership
- Evidence: `.auto-flow/CONTRACT-001-OWNER-MATRIX.md` 完成 219 个顶层类型与 66 个 nested 类型的 owner、例外、模块包名、Dubbo group/version、matched release 和 DEC-011 盘点；common extraction 新增八个 common types；Submission/Notification API artifacts、调用方/POM/provider references、canonical DTO codec/catalog 已迁移；本地 cutover grant/parity/runtime evidence 通过；direct SubmissionProviderContractTest 与 Submission module focused test 通过；Graphify 已刷新
- Rollback: contract package/source 回到上一 verified artifact；runtime cutover 只走 route/grant/watermark/reconciliation runbook；本次 provider 删除故障先回滚到上一已验证 artifact，不改 migration。未执行生产动作，也不声称存在生产环境。
