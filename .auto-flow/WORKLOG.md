# Worklog

## 2026-08-20 architecture review closure

- Parsed /tmp/architecture-review-20260820-045601.html and mapped all candidates to ARCH-REVIEW-001..005.
- Existing architecture review implementation remained in HEAD 32268cf9b; the new reviewer report exposed five additional CRs.

## 2026-08-20 reviewer CR remediation

- Fixed missing AccountQueryService import and made AccountStatsSummary Serializable for Dubbo/Hessian transport.
- Added Auth-owned role grouping to dashboard summary and real MySQL mapper coverage.
- Added page-level user batching to App and Submission projections; preserved single-row read semantics after HiddenCaseLeakIT exposed strict-stubbing drift.
- Extended atomic Search DLQ transfer with owner/schemaVersion/causationId/traceId envelope fields and real Redis E2E assertions.
- Focused, real MySQL/Redis/Meili, affected reactor, full verify, graphify and fresh integration XML checks passed.
- No commit, push, publish, deploy or production action performed.

## 2026-08-20 report execution planning

- Parsed `/tmp/architecture-review-20260820-164414.html`; mapped Candidate 01-05 and terminal recommendation to `AR20260820-001..006`.
- Preserved historical `ARCH-REVIEW-001..005` done state and evidence; did not reuse those IDs for the new source report.
- Chose ordered execution: DevStack → Admin read seam → App Submission seam → Account/Profile schema → Search disposable verification → final audit.
- Set `AR20260820-001` ready; later tasks remain dependency-gated.

## 2026-08-20 AR20260820-001 implementation

- Added `scripts/dev/devstack-manifest.sh` as the single local order/runtime/readiness declaration and a shell contract test.
- `up.sh` now applies all five Owner chains in deterministic order, auto-adopts only the canonical shared-chain bootstrap tables through the existing DEV-LOCAL baseline guard, provisions/unlocks all five runtime accounts, and verifies each account with `SELECT 1` before PM2.
- Hardened owner migration preflight for MySQL 9.1 root's explicit privilege shape and required global grant option for Notification/Submission owner grants; non-root global capability lists remain rejected.
- Fresh disposable MySQL 9.1 `up.sh --skip-infra --skip-bootstrap --skip-install --prepare-submission-owner --mode dev-lite` passed through all migrations, provisioning, readiness, and the no-PM2 exit. Existing owner-safety and migration-preflight suites also passed.
- Compose dev/prod config, default local routing assertions, shell syntax, graphify refresh, and diff checks passed. Development/TEST-TARGET only; no commit, push, publish, deploy, or production action.
- Contextual review completed with Confirmed Findings=0; unrelated dirty-worktree graph impact was isolated from this task. Handoff moved to final validation; task remains `in_progress`.
- Tier C validation passed: final syntax/manifest/preflight, Compose dev/prod, control-plane YAML, help contract, diff check, and disposable cleanup. No full services suite was required because Java sources were unchanged and fresh smoke executed the Flyway Maven path.
- Completion/Coverage Audit passed for AR20260820-001; task set to `done`, AR20260820-002 promoted to `ready`, and the parent objective remains open. No commit or external delivery authorized.

## 2026-08-20 AR20260820-002 recovery and planning

- Promoted `AR20260820-002` to `in_progress` after re-reading the control plane, Candidate 02 report source, Admin source/config/tests, existing owner read contracts, and protected worktree state.
- Confirmed current root cause: `DashboardMapper` in Admin directly selects App/Submission/Solution/Forum/Contest tables while `BackendAdminApplication` scans only Admin-owned mapper packages and Admin runtime uses `ADMIN_DB_NAME=admin`.
- Planned one Admin-owned Dashboard read seam backed by an App-owned aggregate contract and the existing Submission Admin read seam; preserve `DashboardStatsVO`, chart whitelist/period behavior, bounded owner calls, and fail-closed unavailable semantics. No new service, schema, writer, or production cutover.
- Implemented entity-free App Dashboard aggregate/chart contract/provider and Submission dashboard stats/chart extension; Admin now crosses `AdminDashboardReadPort` with parallel bounded owner calls and an 800ms total deadline. Deleted Admin `DashboardMapper` and kept Admin mapper scanning owner-local.
- Focused tests, full affected reactor unit suite, App Dashboard provider IT, Submission owner provider IT, MapperScan/foreign-SQL boundary checks, docs, and graphify refresh passed. No commit/push/deploy/production action.
- Implementation handoff entered Review with Confirmed Findings pending; the only review-sensitive compatibility choice is the additive SubmissionAdminReadPort dashboard methods, with all three in-repo providers updated and affected reactor coverage green.
- Contextual Review completed with Confirmed Findings=0. Direct source fallback was used for changed Java files whose graph metadata was fresh/partial; no hidden implementor or owner-boundary gap remained.
- Tier C Validation passed: affected reactor `verify` (1399/0/0/13), real App/Submission owner ITs, Admin authorization/seam tests, foreign-SQL/MapperScan boundary, graph refresh, YAML parse, diff and status gates. No commit or external delivery.
- Completion/Coverage Audit passed for AR20260820-002; task set to `done`, AR20260820-003 promoted to `in_progress`, and the parent objective remains open.

## 2026-08-20 AR20260820-003 implementation

- Centralized local/remote route choice and remote-provider failure in `SubmissionRoutingProperties.select(...)`.
- Updated Submission write, generation-fence, and user-read routing wrappers to share the same migration policy; no dual calls or adapter deletion.
- Focused routing/property/feature-flag/wiring checks passed. Preserved dev-lite local default, dev-full cutover gate, legacy dispatcher and rollback seam.
- Contextual review and Tier C validation passed for AR003: Confirmed Findings=0; App verify 1401/0/0/13, routing assertions, graphify, YAML and diff checks all PASS.
- Completion/Coverage Audit passed for AR20260820-003; task set to `done`, AR20260820-004 promoted to `in_progress`, and parent objective remains open.

## 2026-08-20 AR20260820-004 implementation

- Recovered Candidate 04 and confirmed the drift: Auth Java projections already select account fields, App writers already write `user_profiles`, but Auth owner DDL and the owner backfill still carried profile-shaped account inserts/parity.
- Added Auth-owner contract migration `V20260820180000__Narrow_Auth_Users_To_Account_Ownership.sql`; did not edit the applied Auth expand migration or shared contract migration.
- Updated `owner-user-profile-backfill.sh` to project account fields only into Auth, keep all nine profile fields in App, preserve soft-deleted rows, use manifest/checksum version 2, and require quiesced explicit `contract-preflight` before contract.
- Added `AuthProfileSchemaOwnershipIT` with real MySQL 8.0 migration/schema/account-mapper evidence; existing App profile writer/provider coverage remained green.
- Contextual Review completed with Confirmed Findings=0. The migration is explicit/destructive only after separately authorized preflight; rollback remains manifest-backed before contraction and forward-migration/backup after contraction.
- Tier C validation passed: Auth verify 217/0/0/0; new Auth schema IT 2/0/0; App profile writer/provider focus 19/0/0; owner migration safety integration on MySQL 9.1; migration preflight, shell syntax, graphify and diff checks.
- Completion/Coverage Audit passed for AR20260820-004; task set to `done`, AR20260820-005 promoted to `in_progress`, and parent objective remains open. No commit, push, publish, deploy, or production action.

## 2026-08-20 AR20260820-005 implementation

- Recovered Candidate 05 and confirmed the proof gap: the existing worker E2E used a real Redis stream plus a Meili HTTP stub, while the real projection test was separately env-gated; no one disposable test showed event → worker → real Meili → `SearchReadProjection`.
- Added test-only `SearchEventToQueryE2EIT` in App and a test-scope `backend-search` dependency; no runtime module, writer, event contract, route or production configuration changed.
- The harness uses unique stream/group/DLQ/version keys and real Redis 7 + Compose-matched `getmeili/meilisearch:v1.8`; it proves duplicate UPSERT convergence, DELETE tombstone/stale suppression, full DLQ envelope and DB fallback.
- First focused compile exposed only an incorrect `StreamRecords` import and a generic Redis stream type; both were corrected and the focused run passed.
- Contextual Review completed with Confirmed Findings=0. Test dependency remains test scope, disposable resources are isolated and cleaned, and existing owner/writer boundaries are unchanged.
- Tier C validation passed: real E2E 3/0/0; worker focus 15/0/0; App Search focus 32/0/0; affected App/Search verify BUILD SUCCESS with App 1401/0/0/13; Compose/YAML, graphify and diff checks passed.
- Completion/Coverage Audit passed for AR20260820-005; task set to `done`, AR20260820-006 promoted to `in_progress`, and parent objective remains open. No commit, push, publish, deploy, or production action.

## 2026-08-20 AR20260820-006 terminal audit

- Audited the report top recommendation and `#do-not-split`: Candidates 01–05 are evidence-backed; no additional runtime module was added, `judge-runtime` remains the existing storage-free implementation, and rollback/local compatibility paths remain.
- Full services `./mvnw verify -B` completed with BUILD SUCCESS in 01:26; current focused owner migration, Auth/App, Search Redis/Meili and Judge runtime checks are green. Fresh direct integration evidence records 68 reports, 225 tests, 0 failures, 0 errors, 17 skips.
- Judge boundary audit confirmed explicit `JudgeRuntimeConfiguration` imports and no datasource/MyBatis/business-table path in the worker; the environment-gated real Redis test is explicitly skipped when `REDIS_HOST` is absent, not reported as passed.
- Control-plane YAML, graph refresh, diff check, rollback inventory and development-only authority audit passed. No unresolved Confirmed Findings or blockers remain.
- Completion/Coverage Audit passed for AR20260820-006; all tasks are `done`, parent architecture-review objective is complete, and no commit, push, publish, deploy, cutover, grant or production action occurred.

## 2026-08-21 review-fix closure

- Recovered two review findings and one fresh reactor issue: shared-Flyway Auth contract no-op, Dashboard canonical App schema/typed mapper rows, and test-only Search module order.
- Focused real owner/MySQL regressions passed; fresh services verify passed with exit 0 and 829/2792/0/0/29 Surefire totals.
- Broad integration selector remains host-timeout-limited and is not counted as pass evidence; no production authority, commit, push, deploy, grant or cutover action performed.

## 2026-08-21 CRFIX-REVIEW-006

- Restored `V20260820180000__Narrow_Auth_Users_To_Account_Ownership.sql` byte-for-byte to the parent version and added the later guarded `V20260821100000__Guard_Auth_Users_To_Account_Ownership.sql`.
- Changed shared Flyway and SearchBackfill test locations to `migrations/*.sql`, preventing owner-directory migrations from running in the shared chain; fresh SearchBackfill MySQL evidence passed 5/0/0/0.
- Restored Dashboard problem chart bucketing to `published_at` and grouped problem status by the derived CASE expression; Dashboard real MySQL/provider/projection focus passed 11/0/0/0, including draft exclusion and deleted-bucket merge.
- Auth follow-up/schema IT passed 2/0/0/0; fresh `services/./mvnw verify -B` exited 0 with 757 fresh reports, 2557 tests, 0 failures, 0 errors and 20 skips.
- `migrate-owner-preflight-test.sh`, MySQL 9.1 owner migration safety rehearsal (with inherited local container overrides cleared), Compose dev/prod config, graphify update and diff check passed. Development/TEST-TARGET only; no external delivery.

## 2026-08-21 architecture review full implementation reopened

- User explicitly requested implementation of all five candidates from
  `/tmp/architecture-review-20260821112953.html`, including the two items
  previously recorded as rejected background candidates.
- Recovered the completed prior ledger and protected its evidence; appended
  `ARCH-20260821-001..006` rather than reusing historical task IDs.
- Execution decision: centralize mode exports in the existing DevStack
  manifest; make Judge-only assembly explicit without adding a process;
  reduce Admin analytics to query slices; add one bounded user-facts batch
  path; make Search database/indexed mode and fallback policy explicit.
- No business implementation has been changed in this phase. Active task is
  `ARCH-20260821-001`; authority remains development/TEST-TARGET only.

- Planning completed for `ARCH-20260821-001..006`: one ordered DAG, no schema
  migration or new runtime process, explicit rollback through existing mode
  variables/adapters, and Tier-C validation for cross-runtime/config/read-seam
  behavior. Handoff moved to implementation of Task 001.

- ARCH-20260821-001 implemented: `devstack_apply_mode` now owns route, Judge,
  Admin cutover and Search read defaults; dev-full includes Search; manifest
  contract, shell syntax, help and ecosystem assertions passed.
- ARCH-20260821-002 implemented: Judge-only worker, attempt executor, stream
  reaper and legacy migration lost automatic stereotypes and remain explicitly
  imported by `JudgeRuntimeConfiguration`; App context absence and Judge wiring
  tests passed. The focused run found and fixed the pre-existing judge POM
  literal `@{argLine}` failure before passing 2/0/0/0.
- ARCH-20260821-001/002 focused review found no hidden caller, Owner, queue,
  rollback or public contract finding; Task 003 promoted to ready.

- ARCH-20260821-003 implemented the Admin analytics vertical slices: the local
  `AdminAnalyticsPort` now exposes contest, revenue and overview reads while
  per-metric fan-out remains private to its adapter. Four reporter/service and
  adapter tests passed (20/0/0/0); no foreign entity or owner contract moved.
- Task 004 is now ready for the owner-composed user-facts batch change.

- ARCH-20260821-004 implemented the bounded user-facts batch seam. Auth account
  IDs and App profiles are fetched once each; Moderation preserves input order
  without per-ID `selectById`. Focused App tests passed 6/0/0/0 after fixing a
  strict-stubbing mismatch by normalizing IDs at the adapter seam.
- Task 005 is now ready for explicit Search database/indexed read semantics.

- ARCH-20260821-005 implemented `SearchReadProperties`: DATABASE mode never
  touches Meili; INDEXED mode requires Meili unless the explicit fallback policy
  is enabled. Unit/config focus passed 24/0/0/0 and real Redis 7 + Meili v1.8
  event-to-query E2E passed 3/0/0/0.
- All five implementation Tasks are now evidence-backed; ARCH-20260821-006
  entered in_progress for full contextual Review, Tier C validation and
  Completion Audit.

## 2026-08-21 architecture review terminal closure

- Closed the two review rework loops: Admin owner fan-out now uses the shared
  bounded/cancellable executor with regression tests; DevStack manifest owns
  timing and per-app readiness banners, and non-Judge PM2 readiness no longer
  depends on the Judge startup log.
- Search indexed mode now requires explicit `SEARCH_WORKER_ENABLED=true`;
  production Compose passes the existing worker gate to App, and responses
  expose mode/source/freshness/order/total/fallback semantics. Console search
  types were updated additively and type-check passed.
- Final Review: Standards=0, Spec=0 Confirmed Findings. Final validation:
  services verify exit 0, 834 reports / 2805 tests / 0 failures / 0 errors /
  29 skips; Redis 7 + Meili v1.8 Search E2E 3/0/0/0; Compose, manifest,
  graph, coverage, wiki manifest, YAML and diff checks PASS.
- Completion Audit: all five report candidates mapped to done Tasks, rollback
  paths preserved, no migration edited, no unrelated changes discarded, and
  authority remains development/TEST-TARGET only. No commit/push/publish/
  deploy/cutover/grant action.
- 2026-08-21: Recovered existing terminal `ARCH-20260821-001..006` as historical evidence only; it belongs to `/tmp/architecture-review-20260821112953.html`, not the current `/tmp/architecture-review-20260821-163916.html` report.
- 2026-08-21: Opened new `ARCHREV-20260821-001..006` DAG for the current five candidates. Scope is development/TEST-TARGET only; no applied migration, production route/grant, commit, push, publish or deploy is authorized.
- 2026-08-21: Implemented Owner fail-closed datasource placeholders, manifest-backed named dev modes, shared submission-api Judge transport contracts, shared judge-config flags/validator, and the full UserFactsProjection caller migration.
- 2026-08-21: Review repair loop closed the Spring placeholder syntax finding, Projection owner-call failure paths, Search concrete-cast test seam, Moderation bypass, CI dead flag, and CancellableQueryExecutor validation race.
- 2026-08-21: Final evidence passed: services verify 843/2827/0/0/29, Judge Redis 4/4, Search E2E 4/4, owner safety/preflight PASS, Compose/manifest/YAML/diff PASS, Review Standards/Spec APPROVE.

## 2026-08-21-221346 architecture transformation recovery and planning

- Reconciled the new user objective against the existing terminal ARCHREV
  ledger; preserved the older objective as historical and created
  ARCHX-20260821-001..006 for the new report.
- Confirmed current gaps from source: DevStack defaults and Judge flags still
  select legacy RQueue in normal dev-lite, SubmissionReadPort is single-row
  while Contest maps one read per submission, UserFactsProjection mixes
  summary and facts shapes, and durable docs/CONTEXT contain stale ownership
  and topology claims.
- Planning decision: use an additive Submission batch contract, narrow
  internal User Facts interfaces without duplicating the composition
  implementation, make Streams canonical in normal development, isolate
  legacy behind an explicit mode, and add source-backed architecture checks.
- Authority: development/TEST-TARGET only; no production action or applied
  migration edit.

## 2026-08-21-221346 ARCHX-003 implementation and review

- Added an additive SubmissionReadPort.toVOs(Collection) contract with input
  order preservation and missing-row omission.
- App and Submission projections now batch user/problem facts; Submission
  detail/best paths reuse the batch seam; Contest no longer maps one remote
  read per submission.
- Added contract, projection, Contest no-N+1 and real Submission owner batch
  coverage. Submission API/owner and disposable MySQL provider checks pass.
- App reactor compilation remains blocked by pre-existing Lombok-generated
  accessor errors outside this slice; source review found no confirmed
  batch-contract finding. ARCHX-004 is now active.

## 2026-08-21-221346 ARCHX-004 implementation and review

- Added UserDirectoryProjection for UserSummaryView directory and account
  reads; UserFactsProjection now exposes only UserFactView batch composition.
- DefaultUserFactsReadProjection implements both narrow seams, so account/profile
  batching remains single-owner-composed and Search/Moderation callers keep
  their existing unavailable-owner semantics.
- Added interface surface regression coverage and migrated directory callers
  and context mocks. Source scan found no summary reads through UserFactsProjection.
- Review found no confirmed finding. App reactor test execution remains
  blocked by the unrelated Lombok accessor baseline; ARCHX-005 is active.

## 2026-08-21-221346 ARCHX-005 implementation and focused gate

- Added scripts/dev/architecture-contract-test.sh and wired it into the
  supported scripts/dev/test.sh path.
- Corrected current architecture status/migration text, README and doctor
  startup guidance, and sharpened CONTEXT.md with User Directory View and
  current Notification Owner worker ownership.
- Executable architecture contract passed: manifest PASS, seam/contract/
  owner/docs assertions PASS, bash syntax PASS, diff check PASS.
- Review found no confirmed finding; ARCHX-006 is now active for full review,
  validation and completion audit.

## 2026-08-21-221346 ARCHX-002 implementation and validation

- Normal dev-lite/dev-full now defaults to Judge Streams, generation fencing and
  JudgeQueue port; legacy RQueue is isolated behind legacy-rollback mode plus
  the existing explicit compatibility flag.
- Added validator tests to the owner module and repaired its inherited
  Surefire argLine so the module test actually executes.
- Focused evidence: manifest PASS; judge-config 5/0/0; Judge 2/0/0;
  Submission 19/0/0. App focused selector exposed an unrelated baseline
  compile failure in Lombok-generated forum/problem-list/solution accessors.
- Review classified no confirmed findings; ARCHX-002 is done. The additive
  Submission batch read slice is now the active task.

## 2026-08-21-221346 ARCHX-001 implementation and review

- Strengthened scripts/dev/devstack-manifest-test.sh to assert all three
  runtime mode defaults, manifest sourcing, supported onboarding commands and
  absence of direct PM2/Maven startup bypasses.
- Replaced README startup/restart instructions with scripts/dev/up.sh mode
  commands; PM2 remains a log/status surface only.
- Focused evidence passed: manifest contract exit 0, bash syntax exit 0 and
  git diff --check exit 0.
- Contextual Review: no correctness, compatibility, security or scope
  findings; Task ARCHX-001 is done and ARCHX-002/003/004 are ready.

## 2026-08-21-221346 ARCHX-006 final review, validation and closure

- Final contextual review found and fixed five issues: duplicate Spring property
  conditions, missing entity batch facts mapping, a missing regression-test
  import, stale Admin terminology, and three legacy startup bypass aliases.
- App affected reactor passed: 1425 tests, 0 failures, 0 errors, 13 skips.
- Full services `./mvnw verify -B` passed: 801 reports, 2705 tests, 0 failures,
  0 errors, 20 skips; SubmissionReadProviderIT passed 4/0/0.
- Architecture contract, manifest, shell syntax, Compose dev/prod, YAML,
  graphify update and diff checks passed. ARCHX-001..006 are terminal.
- Authority remains development/TEST-TARGET only; no commit, push, publish,
  deploy, production cutover, grant action or applied migration edit.
