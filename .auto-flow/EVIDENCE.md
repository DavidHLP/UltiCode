# Auto-pilot Evidence

Objective: reviewer CR remediation for the architecture review report.

## Final evidence

- Auth contract shape: 16 tests, 0 failures, 0 errors, 0 skipped.
- Auth mapper role-count integration: 2 tests, 0 failures, 0 errors, 0 skipped, real MySQL 8.0.
- App HiddenCaseLeakIT plus DefaultSubmissionProjectionTest: 14 tests, 0 failures, 0 errors, 0 skipped.
- Submission SubmissionUserQueryProviderIT: 8 tests, 0 failures, 0 errors, 0 skipped, real MySQL 8.0.
- Search worker unit: 11 tests, 0 failures, 0 errors, 0 skipped.
- Search worker E2E: 4 tests, 0 failures, 0 errors, 0 skipped, real Redis 7 plus HTTP Meili stub.
- Affected owner reactor test: BUILD SUCCESS.
- Full services ./mvnw verify -B: BUILD SUCCESS.
- Fresh direct integration selector: 68 reports, 225 tests, 0 failures, 0 errors, 17 skips.
- First wrapper run exposed 2 strict-stubbing errors in HiddenCaseLeakIT; single-read semantics were restored and the regression passed on rerun.
- Host RPC timed out while waiting for long integration Maven commands; fresh Surefire XML was independently aggregated with exit 0.

## AR20260820-001 implementation evidence

- `scripts/dev/devstack-manifest-test.sh`: PASS.
- `scripts/dev/migrate-owner-preflight-test.sh`: PASS.
- `scripts/dev/owner-migration-safety-integration-test.sh`: PASS.
- Fresh disposable MySQL 9.1 smoke through `up.sh --skip-infra --skip-bootstrap --skip-install --prepare-submission-owner --mode dev-lite`: shared migration, all five Owner chains, canonical baseline adoption, runtime account provisioning, five account readiness probes, and no-PM2 prepare exit passed.
- Compose dev/prod config, shell syntax, `up.sh --help`, default local routing assertions, `graphify update .`, and `git diff --check`: PASS.
- Final Tier C validation: focused scripts, Compose dev/prod, control-plane YAML, help contract, diff check, and disposable resource cleanup all PASS.
- Completion Audit: AR20260820-001 acceptance, coverage mapping, Confirmed Findings=0, migration/rollback assumptions, user-change isolation, and authorized development-only delivery all PASS; AR20260820-002 is the next ready task.
- AR20260820-002 Validation Packet: affected reactor verify 1399 tests / 0 failures / 0 errors / 13 skips; App/Submission MySQL owner ITs PASS; Dashboard authorization/seam tests PASS; foreign-SQL/MapperScan, YAML and diff checks PASS.
- AR20260820-003 Completion Packet: shared route policy, local/remote/failure semantics, App verify 1401/0/0/13, focused route/flag/wiring tests, rollback preservation, review Confirmed=0, and control-plane consistency PASS; AR20260820-004 is next in_progress task.

## AR20260820-004 Validation Packet

- `AuthProfileSchemaOwnershipIT`: 2 tests, 0 failures, 0 errors, real MySQL 8.0; contract migration removed all nine Auth profile columns, retained account/authz columns, and `AuthAccountMapper` insert/read remained executable.
- Auth affected reactor `./mvnw -pl auth -am verify -B`: BUILD SUCCESS; 217 tests, 0 failures, 0 errors, 0 skipped; coverage checks met.
- App profile ownership focus `./mvnw -pl app/app-web -am ... DefaultAppUserWritePortTest,ProfileWriteProviderIT`: 19 tests, 0 failures, 0 errors; ProfileWriteProviderIT real MySQL 8.0.
- `owner-migration-safety-integration-test.sh`: MySQL 9.1 Auth/App owner migration, account/profile backfill, 2 rows including 1 soft-deleted account/profile, submission grant isolation and cleanup PASS.
- `migrate-owner-preflight-test.sh`, `bash -n scripts/dev/owner-user-profile-backfill.sh`, `graphify update .`, and `git diff --check`: PASS; original applied migrations unchanged.
- Review: Confirmed Findings=0. Authority remains development/TEST-TARGET only; no commit, push, publish, deploy, grant, cutover or production acceptance.

## AR20260820-006 Terminal Audit Packet

- Candidate closure: AR20260820-001..005 are `done`; each report candidate has implementation, review, validation, rollback and Coverage evidence with no unresolved Confirmed Findings.
- Full services `./mvnw verify -B`: BUILD SUCCESS, total time 01:26; all reactor modules including backend-judge and backend-search succeeded.
- Integration evidence: fresh direct selector recorded 68 reports, 225 tests, 0 failures, 0 errors, 17 skips; current owner-migration safety, Search real Redis/Meili, Auth/App MySQL and Judge runtime focused checks also pass. The environment-gated Judge Redis test is explicitly recorded as skipped without `REDIS_HOST`.
- Boundary audit: `JudgeRuntimeConfiguration` uses explicit storage-free imports; no new runtime module or physical judge split was introduced. Existing local/remote/legacy, migration manifest/preflight, Search DLQ/fallback and worker-disable rollback seams remain.
- Authority/config: Compose dev/prod with a disposable key, control-plane YAML, graphify update and `git diff --check` pass. Development/TEST-TARGET only; no production acceptance, commit, push, publish, deploy, cutover or grant action.

## AR20260820-005 Validation Packet

- `SearchEventToQueryE2EIT`: 3 tests, 0 failures, 0 errors, real Redis 7 and real `getmeili/meilisearch:v1.8`; event envelope reached the worker, real Meili index, and `SearchReadProjection` query.
- The disposable harness verified duplicate UPSERT convergence, DELETE tombstone and stale-upsert suppression, atomic full-envelope Redis DLQ transfer, and DB fallback on a Meili connection failure.
- Existing Search worker focus: `SearchDocumentIndexWorkerTest` 11/0/0 and `SearchWorkerEndToEndIT` 4/0/0. App Search focus (new harness + projection/source/publisher tests): 32/0/0.
- Affected App/Search reactor verify: BUILD SUCCESS; App 1401 tests / 0 failures / 0 errors / 13 skips; backend-search package SUCCESS and no runtime dependency was added.
- Compose dev/prod config with disposable `MEILI_MASTER_KEY`, control-plane YAML, `graphify update .`, and `git diff --check`: PASS.
- Review: Confirmed Findings=0. Authority remains development/TEST-TARGET only; no commit, push, publish, deploy, grant, cutover or production acceptance.

## Authority

Development/TEST-TARGET only. No commit, push, publish, deploy, production action, or production acceptance claim.

## 2026-08-21 review-fix validation

- Reproduced and fixed `SearchBackfillReadPortIT` migration error: shared Flyway scanning no longer fails when the Auth profile columns are already absent; focused result 5 tests / 0 failures / 0 errors.
- Replaced dashboard mapper `Map<String,Object>` rows with typed `DashboardBucketCount` JavaBeans and aligned App dashboard SQL/tests with the canonical App owner schema; Dashboard unit/IT and Submission owner/adapter focus passed 12 tests / 0 failures / 0 errors.
- Moved `backend-search` before `app` in the Maven reactor so the test-only Search E2E dependency compiles in a fresh reactor.
- Fresh `services/./mvnw verify -B`: `MAVEN_EXIT=0`, BUILD SUCCESS; Surefire XML 829 reports / 2792 tests / 0 failures / 0 errors / 29 skips.
- Broad `*IT,*IntegrationTest` selector was attempted after fixes but the host tool timed out at 300 seconds before an exit code; this is recorded as unavailable, not pass evidence. Target-specific owner/MySQL/Redis/Meili gates passed.

## 2026-08-21 architecture review terminal packet

- `ARCH-20260821-001..006`: all `done`; all five report candidates are
  mapped to implementation, rollback, review and validation evidence.
- Final Standards and Spec review: Confirmed Findings=0. The two standards
  rework findings were closed by `CancellableQueryExecutor` bounded queue /
  Future cancellation / `@PreDestroy` tests and by passing Search worker
  readiness explicitly through production Compose. The two DevStack spec
  findings were closed by manifest-owned timing and per-app banner checks.
- Final `services/./mvnw verify -B`: exit 0, 834 reports / 2805 tests /
  0 failures / 0 errors / 29 skips. Final disposable Redis 7 + Meili v1.8
  Search E2E: 3/0/0/0. Admin concurrency focus: 9/0/0/0. Search semantic
  focus: 25/0/0/0. App context: 6/0/0/0. Judge wiring: 2/0/0/0.
- Final gates: manifest contract, shell syntax, Compose dev/prod config with
  disposable in-memory `MEILI_MASTER_KEY`, console type-check, wiki manifest,
  fresh graphify update, fresh UltiCode-current graph coverage and
  `git diff --check` all pass.
- A subsequent forced MCP reindex after a non-behavioral Search properties
  javadoc/ledger update timed out at the 300s tool limit; the latest
  successful MCP generation is recorded above, and the final graphify update
  plus direct source/coverage checks passed. This timeout is not reported as
  a pass.
- Authority: development/TEST-TARGET only. No production acceptance,
  commit, push, publish, deploy, cutover, grant, or applied migration edit.
