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

## Existing architecture review 2026-08-20 coverage

| Requirement / finding | Task | Required evidence |
| --- | --- | --- |
| Search worker DLQ 的 `XADD` → `XACK` crash window 必须收敛为幂等原子状态转移 | ARCH-REVIEW-001 | exact worker/queue source trace; disposable Redis crash-window regression; source PEL/DLQ/ACK assertions; focused tests; reactor compile/test; diff check |
| Submission read Projection 页面内不得逐行远程 enrichment | ARCH-REVIEW-002 | exact caller trace; page-level batch facts/users; missing/fallback semantics; N+1 regression; focused Submission tests; reactor compile/test |
| Admin dashboard 不得隐藏全量 Auth 分页 fan-out | ARCH-REVIEW-003 | Auth-owned bounded summary contract; window/freshness/unavailable semantics; Admin projection tests; bounded-call assertion; reactor compile/test |
| 开发运行时以 canonical profile 为默认真相，generic DB 变量仅供 migration bootstrap | ARCH-REVIEW-004 | profile/startup assertions; explicit Owner config fail-closed runtime test; Compose dev/prod config; clean-checkout smoke; docs consistency |
| 所有评审任务完成且不制造 production acceptance | ARCH-REVIEW-005 | focused/module/integration/security checks; formal review; graphify update; YAML/diff checks; development-only authority audit; no unresolved mapped item |

Historical task IDs above remain `done` in TASKS.yaml and are not superseded.

## Report /tmp/architecture-review-20260820-164414.html execution coverage

| Requirement / report item | Task / acceptance | Required evidence |
| --- | --- | --- |
| Candidate 01: dev-lite is a real local module; Owner migrations/readiness deterministic; dev-full explicit | AR20260820-001 / all ACs | `up.sh`, migration manifest/config and `ecosystem.config.cjs` trace; fresh-schema smoke; negative default-profile check; Compose consistency |
| Candidate 02: Admin Dashboard does not directly read foreign Owner tables; one bounded coarse seam | AR20260820-002 / all ACs | Dashboard trace; Admin read contract; unavailable/freshness semantics; no foreign mapper SQL; bounded-call tests; reactor evidence |
| Candidate 03: App Submission compatibility matrix is behind one stable intake seam | AR20260820-003 / all ACs | routing trace; adapter inventory; validator/property tests; dev-lite local default; rollback preservation |
| Candidate 04: Account/Profile schema and implementation ownership agree | AR20260820-004 / all ACs | drift trace; later migration; applied boundary/preflight; account-only projection; profile writer exclusivity; schema-contract evidence |
| Candidate 05: Search write/read closure has one disposable verification seam | AR20260820-005 / all ACs | SearchReadProjection/worker trace; Redis→Meili→query envelope; idempotency; DLQ/fallback; disposable E2E; no runtime expansion |
| Report `#do-not-split`, lines 296-298: 不通过增加更多模块收敛；不再物理拆分已是 storage-free implementation 的 judge-runtime；不删除 rollback path；local rehearsal 不得宣称 production authority | AR20260820-006 / terminal AC | no-module-expansion decision; judge-runtime storage-free boundary check; rollback-seam inventory; development-only authority review and no production acceptance evidence |

### AR20260820-004 completion evidence

- Drift trace: Auth account entity/query mapper is account-only; App `DefaultAppUserWritePort` and `ProfileWriteProvider` write `user_profiles`; backfill now has separate account/profile projections.
- Contract evidence: `V20260820180000__Narrow_Auth_Users_To_Account_Ownership.sql` plus real MySQL schema IT proves the nine profile columns are absent while account/authz columns and Auth account mapper behavior remain.
- Expand/verify evidence: manifest v2, checksum parity, duplicate/orphan checks, soft-deleted source inclusion, quiesced `contract-preflight`, and owner migration safety integration all pass.
- Rollback/authority: applied migrations remain untouched; manifest-backed rollback remains available before contraction; no production or external authority action was performed.

### AR20260820-005 completion evidence

- Harness seam: test-only App dependency on `backend-search`; existing `SearchDocumentIndexWorker` and `SearchReadProjection` are exercised together without a runtime split.
- Real transport: Redis 7 stream plus `getmeili/meilisearch:v1.8` passed event → index → query, duplicate write convergence, DELETE/tombstone ordering and full DLQ envelope.
- Fallback: Meili connection failure routed the same projection request to the stub owner `SearchSource` DB path; existing unit and stub-transport tests stayed green.
- Scope/authority: unique disposable keys and container lifecycle prevent local-state mutation; no production acceptance, deployment or new runtime module was performed.

### AR20260820-006 terminal evidence

- All report requirements are mapped to AR20260820-001..005 completion packets; the top DevStack recommendation is complete and the three `do-not-split` constraints are preserved.
- Full reactor verify, fresh integration evidence, current owner/MySQL/Redis/Meili/Judge focused gates, Compose/YAML, graph and diff checks provide the final validation basis; any environment-gated skip is named rather than counted as a pass.
- No new runtime module was added to force convergence; `judge-runtime` remains storage-free and rollback seams remain. The working tree retains pre-existing user changes and this task's uncommitted implementation; no external delivery was authorized.


- Objective: 完成 `/tmp/architecture-review-20260820-164414.html` 五个候选任务及终态审计。
- In scope: DevStack/dev-lite；Admin read seam；App Submission seam；Account/Profile schema；Search disposable verification；tests/config/docs/control-plane。
- Out of scope: 新物理服务或基础设施；RocketMQ/Seata/Kubernetes/Service Mesh；生产 deployment/cutover/publish；删除 rollback seam；未经裁决的 writer/schema 重构；commit/push。
- Root cause: Owner/Contract 方向已成形，但启动、Admin foreign reads、App compatibility、schema ownership 和 Search E2E 仍有边界泄漏或证据缺口。
- Invariants: Owner single-writer；source ownership 不转移；bounded reads；Submission snapshot；public compatibility；explicit Owner config fail-closed；Search sole writer/idempotent/fallback；expand-verify-contract；rollback seam；development-only authority。
- Delivery authority: 仅 development/TEST-TARGET；`.auto-flow/` 不暂存。
- Terminal condition: AR20260820-001..006 均 done，Required Evidence 全部记录，Confirmed Findings=0，验证和控制面审计通过。
