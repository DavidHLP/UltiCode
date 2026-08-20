# Coverage

## Services architecture hardening (2026-08-19)

| Requirement / finding | Task | Required evidence |
| --- | --- | --- |
| P0 首次启动不能依赖 Submission cutover | ARCHFIX-001 | dev-lite first-run smoke、dev-full gate-negative、README/.env/up.sh consistency |
| P0 Auth/Admin/App/Notification 假共享数据库 seam | ARCHFIX-002 | owner schema/account config、privilege-denial tests、migration identity/preflight、Compose/startup evidence |
| P1 Admin/App 双向耦合、细粒度 RPC/N+1 | ARCHFIX-003 | coarse query contract、caller migration、batch/timeout/partial-failure tests、reactor evidence |
| P1 Search 两个行为不同 implementation | ARCHFIX-004 | single default implementation、pagination/total/fallback contract、real MeiliSearch E2E、failure evidence |
| P2 开发运行时认知负担高 | ARCHFIX-001 | minimal default stack and explicit full profile documentation |
| 退役开发 runtime 的 local/remote/legacy/shadow compatibility behavior | ARCHFIX-005 | owner/switch/rollback/observability/retirement inventory、single-writer scan、formal review；rollback seams preserved |
| 全部问题完成且不伪造 production 证据 | ARCHFIX-006 | fresh focused/module/integration/security evidence、control-plane audit、no unresolved mapped requirement |

Historical `.auto-flow/SERVICES_AUTONOMY_*` coverage remains authoritative for its prior ledger and is not overwritten by this plan.

## Architecture review 2026-08-20 coverage

| Requirement / finding | Task | Required evidence |
| --- | --- | --- |
| Search worker DLQ 的 `XADD` → `XACK` crash window 必须收敛为幂等原子状态转移 | ARCH-REVIEW-001 | exact worker/queue source trace; disposable Redis crash-window regression; source PEL/DLQ/ACK assertions; focused tests; reactor compile/test; diff check |
| Submission read Projection 页面内不得逐行远程 enrichment | ARCH-REVIEW-002 | exact caller trace; page-level batch facts/users; missing/fallback semantics; N+1 regression; focused Submission tests; reactor compile/test |
| Admin dashboard 不得隐藏全量 Auth 分页 fan-out | ARCH-REVIEW-003 | Auth-owned bounded summary contract; window/freshness/unavailable semantics; Admin projection tests; bounded-call assertion; reactor compile/test |
| 开发运行时以 canonical profile 为默认真相，generic DB 变量仅供 migration bootstrap | ARCH-REVIEW-004 | profile/startup assertions; explicit Owner config fail-closed runtime test; Compose dev/prod config; clean-checkout smoke; docs consistency |
| 所有评审任务完成且不制造 production acceptance | ARCH-REVIEW-005 | focused/module/integration/security checks; formal review; graphify update; YAML/diff checks; development-only authority audit; no unresolved mapped item |

### Architecture review execution packet

- Objective: 在 development/TEST-TARGET 权限边界内完成评审报告中的四项架构收敛，直到全部验收证据闭合。
- In scope: Search DLQ failure transfer; Submission page-level read batching; Auth-owned bounded Admin summary; canonical development profile/config cleanup; tests, local docs and control-plane evidence required by those changes。
- Out of scope: 新增物理服务、RocketMQ/Seata/Kubernetes/Service Mesh、生产 cutover/deployment/publish、删除可逆 rollback seam、writer/schema/migration 改造（除非实现中发现不可避免且另行裁决）。
- Root cause: 已有 owner/contract seam 存在，但 Search 失败转移不是原子状态转移，Submission 读 Projection 未真正使用批量结果，Admin 将 owner 分页细节泄漏到 projection，开发配置仍公开迁移期兼容变量作为 runtime 真相。
- Behavioral invariants: Owner 单写者；Submission facts snapshot 语义不变；HTTP/Result/公共 Dubbo 兼容；至少一次消费不丢消息；DLQ 至多一条逻辑记录；跨 Owner 读取有界、可失败且不逐行 RPC；Auth 拥有账号统计事实；runtime 显式 Owner 配置 fail-closed；rollback source seam 保留；不宣称 production acceptance。
- Delivery authority: 仅限当前 development/TEST-TARGET；不 commit/push/publish/deploy/生产操作，控制面文件不暂存。
- Terminal condition: ARCH-REVIEW-001..005 均 `done`，每项 Required Evidence 已记录，Confirmed findings=0，focused/module/integration/security/formal review 与控制面审计通过。

### Source-to-task mapping

- Candidate 01 / report lines 181-239 → ARCH-REVIEW-001 → AC-001.1..1.4 → Redis crash-window and reactor evidence.
- Candidate 02 / report lines 241-302 → ARCH-REVIEW-002 → AC-002.1..2.4 → batch/N+1 and missing/fallback evidence.
- Candidate 03 / report lines 304-361 → ARCH-REVIEW-003 → AC-003.1..3.4 → bounded summary and provider unavailable evidence.
- Candidate 04 / report lines 363-424 → ARCH-REVIEW-004 → AC-004.1..4.4 → profile/config/Compose evidence.
- Recommendation and skipped directions / report lines 426-446 → ARCH-REVIEW-005 → AC-005.1..5.3 → no infrastructure expansion and development-only gate.

## Reviewer CR remediation 2026-08-20

| Review finding | Task | Evidence |
| --- | --- | --- |
| AuthAccountQueryPort missing AccountQueryService import; dashboard payload not Serializable | CRFIX-REVIEW-001 | Auth compile; auth-api contract 16/0/0/0 |
| Submission pages still performed single-row user/problem enrichment | CRFIX-REVIEW-002 | App multi-row projection regression; real MySQL Submission IT 8/0/0/0 |
| Auth dashboard summary dropped role counts | CRFIX-REVIEW-003 | Mapper role grouping real MySQL 2/0/0/0; adapter unit test |
| Search DLQ dropped owner/schemaVersion/causationId/traceId | CRFIX-REVIEW-004 | Worker 11/0/0/0; real Redis + Meili E2E 4/0/0/0 |
| All findings re-reviewed and validated | CRFIX-REVIEW-005 | affected reactor PASS; full verify PASS; fresh IT XML 68 reports / 225 tests / 0 failures / 0 errors / 17 skips |
