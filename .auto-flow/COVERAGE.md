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


## ARCHFIX-003 caller seam closure

- `UserSearchBackfillReadPort` -> `UserDirectoryQueryPort.enumerate`
- `DefaultUserSearchReadPort` -> `UserDirectoryQueryPort.search`
- `DefaultAppUserWritePort` -> `UserDirectoryQueryPort.findById`
- `UserSearchReadMapper` and legacy adapter methods removed; no remaining references.
- Evidence: app-web reactor compile PASS; focused Search/App suite 50/0/0/0; `git diff --check` PASS.


## ARCHFIX-003 rework blocker (superseded/closed)

- Historical status was rework/in_progress after the freshness propagation gap. It is superseded by the following freshness closure and evidence entries; no active blocker remains.


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

## ARCHFIX-004 Search coverage (2026-08-20)

| Requirement | Task | Evidence |
|---|---|---|
| single provider | 004-001 | contract/bean scan, no second provider |
| atomic source/read contract migration | 004-001 | four sources, four app-api seams, Solution provider/Admin adapter, UserDirectoryQueryPort, compile |
| specific offset/total | 004-002/003 | source count/offset and Meili request/total assertions |
| all-index deterministic aggregation | 004-002/004 | fixed SearchIndexType order, page-boundary tests and real multi-index E2E |
| cross-Owner user exact count/page | 004-001/002 | account_id ASC batched merge, duplicate filtering, Auth usernameOnly count and profile count/page tests |
| disabled database fallback | 004-003 | MEILISEARCH_ENABLED=false focused/dev-lite evidence |
| whole-request fallback | 004-003/004 | failure injection and real failure evidence |
| public compatibility | 004-002/004 | controller contract and Console build/type evidence |
| canonical Meili environment names | 004-003 | App/Compose/worker config scan and disabled/configured tests |
| no writer/schema/migration change | 004-001..004 | diff/source/migration scans |
| retirement deferred | 005 | retirement inventory and rollback evidence |

Validation tiers: focused -> app-web module -> real MeiliSearch integration -> boundary scans -> formal review.

## ARCHFIX-004-001 closure evidence (2026-08-20)

- Acceptance closed: count-capable SearchSource plus four source implementations and app-api seams compile; specific-index offset reaches the source read seam; user Auth/App merge is bounded, account_id ASC and deduplicated; Solution's existing two-argument Dubbo method remains; no writer/schema/migration changed.
- Review: one specific-index offset finding was fixed and rechecked; Confirmed findings = 0.
- Validation: focused Search/owner suite 38/0/0/0; SolutionApiContractShapeTest and BackendAppApplicationTest passed; affected reactor verify BUILD SUCCESS with 604 Surefire reports, 2029 tests, 0 failures, 0 errors, 16 skips and JaCoCo PASS; graphify/codebase-memory coverage refreshed; YAML parse and git diff --check passed.
- Deferred by dependency: ARCHFIX-004-002 owns all-index DB page allocation and exact response totals; ARCHFIX-004-003 owns Meili whole-request fallback and canonical environment names; ARCHFIX-004-004 owns real Meili E2E.

## ARCHFIX-004-002 validation evidence (2026-08-20)

- Specific-index DB fallback now returns exact source count, forwards page offset/limit and returns empty rows without querying on out-of-range offsets.
- All-index DB fallback uses fixed SearchIndexType order, maps a global offset into source-local offsets, fills the remaining page and sums exact source counts.
- Focused Search/owner suite passed 40 tests with 0 failures, 0 errors and 0 skips; affected reactor verify passed with 604 reports, 2031 tests, 0 failures, 0 errors and 16 skips; JaCoCo passed.
- Review found no Confirmed findings; graphify/codebase-memory coverage, YAML parse and git diff check passed. Meili behavior/config and real E2E remain intentionally deferred to 004-003/004.

## ARCHFIX-004-002 closure evidence (2026-08-20)

- Acceptance closed: specific-index exact totals/offset, all-index fixed-order global page allocation, summed totals and stable out-of-range rows are covered by focused regressions.
- Review and Validation closed with Confirmed=0; affected reactor verify passed with 604 reports, 2031 tests, 0 failures, 0 errors, 16 skips, BUILD SUCCESS and JaCoCo PASS.
- No writer/schema/migration/config change was included; ARCHFIX-004-003 remains the sole owner of Meili fallback/config behavior.

## ARCHFIX-004-003 closure evidence (2026-08-20)

- Meili `SearchResult.getTotalHits()` is accepted only when it is below `pagination.maxTotalHits`; capped totals trigger whole-request database fallback. Specific/all requests preserve fixed index order and offsets.
- Per-index exceptions now escape to the outer whole-request DB fallback; the regression proves the DB path is used after a Meili failure.
- App config, worker config and Compose now use MEILI_HOST / MEILI_MASTER_KEY consistently; both dev and prod Compose config checks passed with an ephemeral validation key.
- Focused Search/Auth reactor suite passed 39/0/0/0 for the final patch; standard integration passed 822 reports, 2769 tests, 0 failures, 0 errors, 29 skips. Review Confirmed=0 after the public-search resource-boundary fix.
- Real Meili E2E is closed by the ARCHFIX-004-004 evidence below; no deferred dependency remains for ARCHFIX-004.

## ARCHFIX-004-004 closure evidence (2026-08-20)

- Actual getmeili/meilisearch:v1.8 disposable service passed health and SDK integration. DefaultSearchReadProjectionRealMeiliIT passed 2/2 with specific offset/total, all-index summed total and whole-request fallback after a broken client.
- Test data used a unique run id and cleanup; the loopback container was stopped and the failed Compose-created disposable volume was removed. The Compose network overlap was not force-repaired.
- ARCHFIX-004 is now fully evidenced locally; production route, deployment, runtime observation and compatibility-retirement authority remain separate gates.

## ARCHFIX-005 development-only retirement gate (2026-08-20)

- User authority: the repository has no production environment; the current development/TEST-TARGET is the sole authorized target for reversible blocker remediation.
- The current `.env` keeps `APP_SUBMISSION_ROUTING_MODE=local`; source/config inventory still shows the remote route, local rollback adapters and shadow components. Retirement must follow the actual caller graph and keep one runnable rollback artifact until the dev gate closes.
- Required development evidence: one active writer and default reader, route/quiesce/observation/rollback checks, compatibility scan, focused single-writer tests, formal review and a clean diff. Development evidence is not production evidence.

## ARCHFIX-005 closure evidence (2026-08-20)

- Runtime retirement: `dev-lite` forces local route with App outbox/generation/Streams flags off; `dev-full` requires remote route/cutover and enables the owner/Streams flags. App and Judge receive the same profile flag set, so local legacy queue work is not left without a consumer.
- Disposable owner gate: `owner-migration-safety-integration-test.sh` passed real Submission copy, App grant revoke, target ownership check, rollback copy-back and grant restoration, followed by table/global/role/routine grant rejection checks.
- Focused route/provider suite passed 22/0/0/0; `migrate-owner-preflight-test.sh` passed; Compose network down/up recreation passed without fixed IPAM/static-address overlap.
- Source-level local adapters, dispatcher and reaper remain rollback seams for dev-lite; no rollback-only source was deleted. No production environment or production action is claimed.

## ARCHFIX-006 closure evidence (2026-08-20)

- `scripts/dev/test.sh quick` passed the backend reactor plus auth-core, console and management tests/type checks; the stale `.env` Meili interpolation blocker was fixed with an in-memory ephemeral key fallback.
- App/Judge profile assertions passed for both dev-lite and dev-full; shell syntax, Node syntax, Compose dev/prod config, YAML parsing and `git diff --check` passed.
- The formal review HIGH finding (dev-lite App/Judge queue mismatch) was fixed and rechecked; no secret leakage or critical finding remained. `graphify update .` completed with 27,957 nodes / 81,621 edges; the nine unrelated config/IDE zero-node warnings remain expected.
- All current ARCHFIX control pointers now agree: ARCHFIX-001..006 done, no active blocker, development-only authority and no production claim.

## CRFIX-001 coverage (2026-08-20)

| Review finding | Implementation | Evidence |
|---|---|---|
| opt-in Real-Meili IT breaks standard suite | environment conditions plus parent Surefire selected-test handling | wrapper integration exit 0; bare `*IT` exit 0; 2 skips without variables |
| capped/estimated Meili total | exhaustive page query, cap check and whole-request DB fallback | cap regression plus real 1,500-document Meili test |
| unstable DB offset pagination | `id ASC` in Problem/Forum/Solution adapters | deterministic SQL wrapper tests for all three |
| collation-inexact user union count | bounded Auth-owner `NOT LIKE` count RPC | owner union tests plus real MySQL `utf8mb4_0900_ai_ci` IT |
| review-loop performance/security findings | count/hit split, 100-ID provider bound, null/overflow/task-status guards | focused suites and two formal re-reviews PASS |
