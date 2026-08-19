# Coverage

## Services architecture hardening (2026-08-19)

| Requirement / finding | Task | Required evidence |
| --- | --- | --- |
| P0 首次启动不能依赖 Submission cutover | ARCHFIX-001 | dev-lite first-run smoke、dev-full gate-negative、README/.env/up.sh consistency |
| P0 Auth/Admin/App/Notification 假共享数据库 seam | ARCHFIX-002 | owner schema/account config、privilege-denial tests、migration identity/preflight、Compose/startup evidence |
| P1 Admin/App 双向耦合、细粒度 RPC/N+1 | ARCHFIX-003 | coarse query contract、caller migration、batch/timeout/partial-failure tests、reactor evidence |
| P1 Search 两个行为不同 implementation | ARCHFIX-004 | single default implementation、pagination/total/fallback contract、real MeiliSearch E2E、failure evidence |
| P2 开发运行时认知负担高 | ARCHFIX-001 | minimal default stack and explicit full profile documentation |
| 最后删除 local/remote/legacy/shadow compatibility paths | ARCHFIX-005 | owner/switch/rollback/observability/retirement inventory、single-writer scan、formal review |
| 全部问题完成且不伪造 production 证据 | ARCHFIX-006 | fresh focused/module/integration/security evidence、control-plane audit、no unresolved mapped requirement |

Historical `.auto-flow/SERVICES_AUTONOMY_*` coverage remains authoritative for its prior ledger and is not overwritten by this plan.


## ARCHFIX-003 caller seam closure

- `UserSearchBackfillReadPort` -> `UserDirectoryQueryPort.enumerate`
- `DefaultUserSearchReadPort` -> `UserDirectoryQueryPort.search`
- `DefaultAppUserWritePort` -> `UserDirectoryQueryPort.findById`
- `UserSearchReadMapper` and legacy adapter methods removed; no remaining references.
- Evidence: app-web reactor compile PASS; focused Search/App suite 50/0/0/0; `git diff --check` PASS.


## ARCHFIX-003 rework blocker

- Status: rework/in_progress. Prior closure invalidated by freshness propagation gap.
- Required: adapter `findById`, `toRow`, and backfill must preserve distinct Auth and profile timestamps and prove `freshAt`.


## ARCHFIX-003 freshness closure

- `findById` reads App `updated_at` via `findSearchRowsByAccountIds`, while Auth `updatedAt` remains separate.
- `toRow` no longer overwrites Auth `updatedAt` with profile watermark.
- `UserDirectoryRow.from` computes `freshAt = max(authUpdatedAt, profileUpdatedAt)`.
- Adapter and backfill freshness regressions pass.


## Freshness blocker rework evidence

- Backfill now consumes `UserDirectoryRow.freshAt()` directly; no legacy watermark recomputation remains.
- `findById` uses Search profile projection with `updatedAt` and treats null mapper results as missing profile.
- Actual workspace focused suite: 23/0/0/0.


## Duplicate account contract closure

- `findByIds` drops null/out-of-request accounts and deduplicates duplicate account IDs with first-result-wins semantics.
- Regression covers duplicate requested account and out-of-request response account.
- Actual focused suite: 24/0/0/0.
