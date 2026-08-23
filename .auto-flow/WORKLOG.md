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

## 2026-08-22 Services review recovery and planning

- Reconciled the new review report against the existing terminal control plane;
  preserved all prior task history and protected the two pre-existing untracked
  files (`services/docs/SERVICES_REVIEW_FINDINGS_2026-08-22.md` and the
  services graphify cache stamp).
- Confirmed graph project `UltiCode` is indexed, but exact coverage metadata is
  unavailable/stale, so every material graph claim was checked against direct
  source. Hindsight pages and source identify current owner/outbox/testing
  conventions; no new initiative page was captured because this is a review
  fix, not a feature initiative.
- Planned the ordered `SVCFIX-20260822-A1..D4` DAG plus final Review/Validation/
  Completion task. First ready task is A1. Authority remains development/
  TEST-TARGET only; no commit, push, publish, deploy, production action or
  applied migration edit.

## 2026-08-22 SVCFIX-A1 implementation

- Removed the CONNECT fallback to client-controlled STOMP headers and deleted
  `TokenExtractor.extractTokenFromHeaders` plus its obsolete tests. Existing
  cookie-session extraction remains the only CONNECT token source.
- Added a regression that puts a token in a native STOMP header but proves the
  authenticator receives `Optional.empty()` and the connection is rejected.
- Focused `app-web -am` test passed: JwtChannelInterceptorTest 11/0/0/0 and
  TokenExtractorTest 6/0/0/0; reactor BUILD SUCCESS, exit 0. A2 is promoted to
  ready.

## 2026-08-22 SVCFIX-A2 implementation

- Made the OAuth callback cookie binding mandatory while preserving atomic
  Redis `getAndDelete`, mismatch rejection and Redis-unavailable fail-closed
  behavior.
- Added null and blank cookie-state regression assertions; Auth focused test
  passed 8/0/0/0 with BUILD SUCCESS and exit 0. B3 is promoted to ready.

## 2026-08-22 SVCFIX-B3 implementation

- Replaced the ineffective `FOR UPDATE SKIP LOCKED` candidate claim with a
  single-row `PENDING -> PROCESSING` CAS. `markProcessed` and `markFailed`
  require `PROCESSING`; the processor refuses a lost CAS before inserting an
  audit log.
- Dispatcher/processor regressions passed 7/0/0/0 with BUILD SUCCESS and exit
  0. The attempted real `AuditSinkTransactionIT` was blocked by the unrelated
  Admin context baseline (`DefaultAdminDashboardReadAdapter` has no default
  constructor), so it is not counted as pass. B6 is promoted to ready.

## 2026-08-22 SVCFIX-B6 implementation

- Updated the Auth role writer to increment `authz_version` in the same atomic
  SQL statement as the guarded role change. Same-role updates remain no-ops.
- `AuthAccountPersistenceIT` passed 3/0/0/0 with BUILD SUCCESS and exit 0,
  proving version 5 -> 6 on a real role change and no second bump on an
  idempotent update. B4-B5 is promoted to ready.

## 2026-08-22 SVCFIX-B4-B5 implementation

- Replaced the RBAC-only structured log with a durable `AUTHORIZATION_CHANGED`
  event through the existing Auth insert-only `admin.audit_outbox` seam. Role
  events carry the B6 version; direct permission grant/revoke paths atomically
  advance `authz_version` before emitting the event, while no-op revokes emit
  nothing.
- Focused Auth event/sink/version suite passed 9/0/0/0 with BUILD SUCCESS and
  exit 0. Added `wiki/SECURITY_TOKEN_REVOCATION.md`; wiki manifest generation
  and lint both passed. D1 is promoted to ready.

## 2026-08-22 SVCFIX-D1 implementation

- Added per-seam kill criteria to the current Services architecture status:
  14-day quiesce/peak observation, route-specific zero-correctness-error
  budgets, residual App-copy retirement gates, and concrete local/remote/
  manifest/outbox rollback artifacts.
- Explicitly preserved Search `database`/`indexed` as a manifest decision.
  Documentation scan and `git diff --check` passed. D2 is promoted to ready.

## 2026-08-22 SVCFIX-D2 implementation

- Added `package-info.java` ownership declarations for the shared
  `judge-runtime` sandbox and queue-port packages, and aligned the Services
  architecture status wording. Trial DTOs remain in `app-api`; no public
  contract move or process split was introduced.
- `judge-runtime -am test` passed 28/0/0/0 with BUILD SUCCESS and exit 0;
  package ownership scan passed. D3 is promoted to ready.

## 2026-08-22 SVCFIX-D3 implementation

- Extended the existing `SubmissionFactsSnapshotTest` with an exact reflection
  shape gate for the five top-level fields and six nested `ProblemFacts`
  fields. No production snapshot field or public contract changed.
- Focused Submission owner/API test passed 7/0/0/0 with BUILD SUCCESS and exit
  0. D4 is promoted to ready.

## 2026-08-22 SVCFIX-D4 implementation

- Removed only the named untracked compiled artifact under `services/com/`.
  Verified exact absence and proved `docs/MICROSERVICE_MIGRATION_GUIDE.md`
  was unchanged by SHA-256, Git status and diff checks.
- D4 focused hygiene gate passed. All implementation tasks are now ready for
  contextual Review, Tier-C validation and Completion Audit.

## 2026-08-22 Services review re-review

- First contextual pass found two confirmed local quality gaps and repaired
  both: removed the now-unused `TokenExtractor` constructor dependency from
  `JwtChannelInterceptor`, and made `AuditOutboxProcessor` fail/roll back when
  the audit-log insert does not affect exactly one row.
- Re-ran the combined WebSocket/Admin focused gate: WebSocket 17/0/0/0 and
  Admin outbox 7/0/0/0, BUILD SUCCESS, exit 0. Re-review is now proceeding
  against the repaired diff; the initial Admin `AuditSinkTransactionIT`
  ApplicationContext baseline was repaired with one constructor annotation.

## 2026-08-22 Services review terminal closure

- Review re-check ended with Confirmed Findings=0. The minimal Spring
  constructor annotation used to unblock the Admin integration context was
  validated separately; it does not alter dashboard behavior.
- Tier-C evidence passed: final compile/test/verify, focused security/OAuth/
  AuditOutbox/RBAC/snapshot gates, Admin `*IT` 44/0/0/0, owner migration
  preflight/safety, architecture/devstack contracts, wiki manifest, Compose
  dev/prod with a disposable in-memory Meili key, YAML/diff/integrity and
  graph refresh. Final verify exited 0; non-IT Surefire XML aggregate is
  783 reports / 2638 tests / 0 failures / 0 errors / 16 skips.
- Root broad `-Dtest='*IT'` rerun exceeded the host 300-second wait without an
  exit status, so it remains explicitly unavailable rather than pass evidence.
- Completion Audit passed for all ten `SVCFIX-20260822` tasks. Worktree stays
  uncommitted; no push, deploy, production action, grant change or applied
  migration edit occurred.

## 2026-08-22 documentation/wiki consolidation recovery and planning

- Inventoried the selected documentation scope: `services/docs/` five files,
  `apps/management/docs/` one file, and `wiki/` one content page plus manifest;
  root `docs/` is absent. Protected `.claude/rules/docs/`, AGENTS, README,
  `.auto-flow` and unrelated dirty-worktree files.
- Planned one root `PROJECT_DOCUMENTATION.md` organized by current architecture,
  Owner/migration, security, release, i18n and review history. Source markers
  and complete source sections are retained for lossless auditability.

## 2026-08-22 documentation/wiki consolidation implementation

- Created `PROJECT_DOCUMENTATION.md` (1764 lines / 144573 bytes); all seven
  source markers and source headings are present.
- Removed exactly the seven content files plus wiki manifest and obsolete
  `scripts/dev/wiki-manifest.sh`; no broad recursive deletion was used.
- Updated AGENTS/README/documentation workflow, executable architecture and
  DevStack checks, migration comments, Javadocs and the owner schema test to
  reference the consolidated file. Architecture/devstack contracts, the
  affected owner schema test, local-link scan and diff check passed. Review,
  Validation and Close remain open.

## 2026-08-22 documentation/wiki review

- Consolidated content review confirms the file has a single navigation layer,
  source provenance for all seven inputs, explicit historical/current status
  boundaries, no broken local links in active entry points, and no active
  references to deleted documentation paths.
- Protected `.claude/rules/docs/`, `.codex` configuration, AGENTS, README,
  `.auto-flow`, source/configuration and unrelated dirty-worktree files remain
  outside deletion scope. Review is now checking final diff and validation.

## 2026-08-22 documentation/wiki review closure

- Review passed: consolidated Markdown has one top-level heading, balanced
  fences, seven source markers, explicit current/historical boundaries, 49/0
  active local links, no active deleted-path references, and protected-worktree
  scope intact. Validation is now the last open gate.

## 2026-08-22 documentation/wiki validation

- Architecture contract and DevStack manifest tests passed; the affected owner
  schema test passed 6/0/0/0. Final local-link scan passed 49/0, selected docs/
  wiki directories are absent, YAML and diff checks passed, and graphify was
  refreshed after the final document/reference changes.

## 2026-08-22 documentation/wiki completion audit

- Completion Audit passed: one consolidated Markdown remains, all selected
  source content is preserved with provenance, active references are migrated,
  deletion scope is exact, and all required documentation gates pass.
- No commit, push, publish, deploy or external resource mutation was performed;
  final delivery is an uncommitted worktree with a recoverable Git diff.

## 2026-08-23 App Owner problemset seed repair

- Reproduced `app.problems=0` and `app.problem_lists=0`; legacy `ulticode`
  contained 6 problems and 9 lists. App Owner Flyway locations intentionally
  exclude the historical shared seed files.
- Added a DEV-LOCAL-only App Owner seed Adapter and wired it after Owner
  migrations in `up.sh`; no applied migration or production path changed.
- Real seed run passed with 6 problems / 6 published / 9 lists / 7 featured;
  second run skipped and preserved existing data. API recheck returned 200 for
  list, overview and random endpoints.
- Completion: full startup exit 0; quick exit 0; services verify real
  `VERIFY_EXIT=0`; category filter returned 6/6; Compose dev/prod, graph refresh,
  documentation and migration contract checks passed. No commit/push/deploy.

## 2026-08-23 Forum 500 schema repair

- Reproduced Console proxy and direct App `GET /forum/posts?sortBy=hot` as
  HTTP 500. App log identified `Unknown column 'is_deleted'`; direct schema
  inspection showed legacy `forum_posts` had only six columns.
- Added `V20260823170000__Align_Forum_Posts_With_Runtime_Contracts.sql` and
  App migration preflight/fixture coverage for DROP and transient routine DDL.
- First Owner safety attempt exposed the pre-existing App DROP privilege gap;
  after updating the explicit migration contract, the disposable rehearsal
  passed all Auth/App/grant isolation gates.
- App migration, idempotent rerun, App tests, quick gate and all five Forum
  sort modes passed; no applied migration was edited.

## 2026-08-23 Forum DEV-LOCAL seed completion

- Confirmed the empty page was a successful `200 code=0 total=0` response with
  `app.forum_posts=0`, not a frontend failure.
- Extended the unified App Owner seed Adapter with the canonical forum seed;
  mapped the legacy admin subquery locally to avoid cross-Owner `users` reads.
- Seeded 12 posts / 3 communities / 6 tags / 12 users; rerun preserved data.
  Official full startup exit 0 retained the data, and all Forum sort modes
  returned 200 with total 12.

## 2026-08-23 Contest DEV-LOCAL seed completion

- Reproduced Contest catalog, upcoming, running and past endpoints as HTTP 200
  with `total=0`; App `contests` and `global_rankings` tables were empty.
- Confirmed the App Contest schema matches the immutable contest and global
  ranking seed columns. Extended the unified App Owner Adapter with one
  transaction for both sources, complete/partial guards, fixture-only IDs and
  a `+08:00` session clock alignment for the App `Asia/Shanghai` lifecycle.
- First run populated 5 contests / 22 contest problems / 22 participants /
  11 rankings / 51 problem results / 7 announcements / 10 global rankings;
  second run preserved the complete fixture set.
- Full `up.sh` startup completed with `Development stack is ready`; proxied
  Contest catalog/upcoming/past/global-ranking APIs returned HTTP 200 and
  non-empty totals. Browser DOM showed 2 upcoming contests, 10 rankings and 3
  past contests.
- Normalized backend `ContestRankingVO.score` to frontend `rating`; console
  schema test 3/3, full console test 579/579, type-check, build, lint and
  architecture/documentation/migration contract checks passed. No commit,
  push, deploy or production action.
- Context review found no confirmed correctness, ownership, transaction,
  security, compatibility or test-quality findings; the stale graph coverage
  probe was qualified and exact source was read directly.

## 2026-08-23 Avatar fallback completion

- Reproduced the personal avatar issue: `app.user_profiles` had zero rows and
  `/users/by-username/admin/profile` returned no avatar field, so the Console
  rendered initials.
- Reused the existing `useAvatar` seam, preserving custom App profile URLs and
  generating a deterministic local SVG data URL when the profile is empty.
  Personal profile and sidebar user surfaces now share the same behavior.
- Browser verification showed two visible `data:image/svg+xml` avatar images;
  avatar tests 2/2, affected tests 14/14, full Console tests 581/581,
  type-check, lint and production build passed. No Auth write, migration,
  commit, push, deploy or production action.

## 2026-08-23 Ranking avatar ownership completion

- Confirmed the ranking fixture stored DiceBear URLs in `global_rankings.avatar`,
  while App `user_profiles` was empty and is the canonical profile owner.
- Changed public `GlobalRankingMapper` display projections to select `p.name`
  and `p.avatar` by `account_id`; the legacy ranking avatar column is no longer
  used for public display.
- Kept the applied legacy seed immutable. The DEV-LOCAL seed Adapter strips the
  known placeholder URLs and clears existing fixture placeholders. The Console
  ranking shows a real profile avatar when present and initials when absent.
- App module tests passed 1420/0/0/13; ranking/avatar tests 4/4; full Console
  tests 582/582; type-check, lint, build, startup/API, docs, graph and diff
  checks passed.

## 2026-08-23 Solution seed completion

- Reproduced the solution page empty state: `/api/api/problems/7/solutions`
  returned HTTP 200 code 0 total=0, while App `solutions` had zero rows.
- Extended the unified App Owner Adapter with the immutable solution seed in a
  separate guarded transaction and mapped the legacy admin subquery locally.
- First run populated 12 solutions; rerun preserved complete data. The feed
  now returns 2 solutions for problem 7 and the detail endpoint returns 879
  characters of Markdown content.
- Browser DOM now shows both seeded solution cards; App/Console tests,
  type-check, lint, build, startup, docs, graph, YAML and diff checks passed.
