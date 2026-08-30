# Services Issues Evidence

- 2026-08-28 recovery started at `main@c3ceb69136b1678fde13461472ce191227a08167`; business worktree clean before control-plane updates.
- Codebase Memory project `UltiCode` ready on `main`, generation/head `c3ceb69136b1678fde13461472ce191227a08167`; `.auto-flow`, migrations, scripts, and tests require direct-source fallback where excluded by design.
- `SERVICES_ISSUES.md` contains SVC-001..005 OPEN, SVC-006..010 DEFERRED, CLOSED history, ACCEPTED decisions, and maintenance rules.
- Recovery graph evidence: `ProblemSubmissionController.runCode -> CodeExecutionService.execute`; `SubmissionRoutingProperties.select` has write/fence/user-read routing callers; `DefaultAdminUserProjection.getUsers` queries Auth then App directly; `AdminUserEnricher.enrich` independently merges Auth/App.
- Coverage fallback: Codebase Memory generation is 2026-08-24 and several evidence files report `metadata_changed`; `scripts/` is excluded. Current Java/POM/workflow/script/doc sources were therefore read directly before planning.
- Graphify broad query returned 2,107 nodes and was truncated at the 3,000-token budget; it was used only to identify `CodeExecutionPort`, `ProblemFactsPort`, Judge runtime, routing, and deployment candidates, never for negative proof.
- SVC-001 focused initial Maven: App 17 tests + Judge 2 tests, BUILD SUCCESS, exit 0; App Spring context 6/6, exit 0.
- SVC-001 Review round 1: 3 HIGH findings confirmed and fixed (legacy rollback conditions, 190-second deadline, submission-controller HTTP advice); follow-up code review closed all three, then documentation was aligned.
- SVC-001 affected-module Maven: BUILD SUCCESS, exit 0; aggregate Surefire XML = 481 reports / 1601 tests / 0 failures / 0 errors / 17 skipped. `architecture-contract-test.sh` and `bash -n` both exit 0.
- SVC-002 focused initial 55/55 and compatibility rework 56/56 passed; `SubmissionAdminReadProviderIT` ran against Testcontainers. Architecture/docs/DevStack gates exit 0.
- SVC-002 N-1 binary proof: temporary `origin/main@4e8e9ff80` baseline install exit 0; current package exit 0; `api/submission-api` japicmp `contract-compat` BUILD SUCCESS / exit 0; temporary worktree removed.
- SVC-002 affected-module Maven: BUILD SUCCESS / exit 0; aggregate Surefire XML = 503 reports / 1671 tests / 0 failures / 0 errors / 17 skipped. Review follow-up Confirmed=0.
- SVC-004: direct source proves immutable intake facts, 100-row read chunks, per-page Problem facts batches, empty-page RPC skip, and batch user lookup. Focused Testcontainers/provider suite 30/30, BUILD SUCCESS / exit 0; Review Confirmed=0, status ACCEPTED.
- SVC-005: deploy/rollback/host-health matrix checks pass; bash syntax and all three shell contracts exit 0; PyYAML parsed 3 changed workflow/action files; minified JSON parser returned 7 backends. Review round 1 fixed HIGH+MEDIUM and follow-up Confirmed=0. Ruby was unavailable (exit 127) and was not counted as pass.
- SVC-006 focused: 26 ran pass, 2 existing Redis/MySQL-dependent BackendAdminApplicationTest cases skipped. Full Admin module: Maven exit 0; 189 Surefire reports / 600 tests / 0 failures / 0 errors / 3 skipped. Architecture/docs gates exit 0; Review Confirmed=0.
- SVC-003-GATE: `.local/evidence/20260819T2000Z/TASK-007/rollback-observation-rehearsal.yaml` says `production window unavailable` and `production cutover remains blocked`; dev-local runbook states live writer drain/external authority unavailable. No required 14-day/error-budget/real-target evidence found. Independent review: blocked_external.
- SVC-007/008/010: current single-host topology, local Docker socket default, and pre-matrix tags are verified; required production topology/threat/release triggers are absent. Tags `v1.2`..`v3.0` all lack `.github/services-matrix.json`.
- SVC-009: all 7 backends require explicit OTLP endpoint; per-service architecture gate, bash, docs, Compose dev/prod config exit 0. Review rework closed missing endpoint and false-green count Findings, final Confirmed=0. Real trace/SLO/live recovery evidence remains external.
- Objective formal Review: initial Standards 1 MEDIUM + 1 LOW and Spec 1 HIGH + 1 MEDIUM; all fixed. Final Standards=0, Spec=0. Security initial 1 LOW internal-topology response leak; fixed, final Security=0.
- Full reactor verify: first run failed App context because test-scope Judge beans entered broad scan; fixed explicit App scan and Dubbo reset. Final `./mvnw verify -B` BUILD SUCCESS / exit 0; 813 reports / 2714 tests / 0 failures / 0 errors / 20 skipped.
- Full integration: first `*IT` reached App with 126 tests, 2 obsolete Mockito-stub errors and 13 skips; focused HiddenCase 2/2 passed. After current upstream install, `-rf :backend-app-web` completed App/Submission/Notification BUILD SUCCESS / exit 0. Final IT XML: 71 reports / 233 tests / 0 failures / 0 errors / 13 skipped.
- Supported quick: first run failed Judge Redis ACL auth because username was omitted; test client now consumes `REDIS_USERNAME`. Rerun ended `All quick checks passed`, exit 0.
- Final N-1: temporary `origin/main@4e8e9ff80` app/submission API baseline install exit 0; current package and japicmp contract-compat exit 0; temporary worktree removed.
- Final static/runtime config: architecture/docs/DevStack contracts, bash syntax, Compose dev/prod config, 3 YAML files, and `git diff --check` exit 0.
- Graphs: final Graphify update 27545 nodes / 80685 edges / exit 0; current Codebase Memory generation indexed 43719 nodes / 188931 edges. Key new paths metadata_match with no recorded gaps; scripts remain excluded and were read directly.

## Architecture remediation 2026-08-30

- Git baseline: branch main, HEAD 8b4012b3d13678eaec38a82980c8e3558123b5a8, origin/main 9672a9765, ahead 1, clean worktree; remediation branch fix/architecture-remediation created before implementation.
- Task graph: .auto-flow/TASKS.yaml parses as YAML with 42 unique current tasks, all required fields, allowed statuses, and CTX-001 as the only IN_PROGRESS task.
- Static baseline: architecture contract, documentation contract, owner migration preflight, Compose dev config, and Compose prod config all exit 0. Logs are in .auto-flow/evidence/architecture-remediation-20260830/.
- Docker baseline: CLI 29.7.2 and Compose 5.5.0 available; daemon access denied because uid 1000 is not in group docker and /var/run/docker.sock is root:docker mode 0660. docker version/info exit 1; Docker-dependent gates are not PASS.
- Maven baseline: clean compile exit 0 in 14s; clean test exit 0 in 86s; clean verify exit 0 in 86s. Surefire: 809 reports / 2739 tests / 0 failures / 0 errors / 20 skipped. JaCoCo line/branch: Admin 62.0%/47.9%, Notification 55.9%/39.4%, App Web 54.1%/42.4%.
- P0-SEC-001 RED: focused Auth test ran 4 tests with 4 assertion failures because emitted Set-Cookie headers lack Secure and SameSite=Lax; no setup or execution errors.
- [降级执行] Java LSP references were unavailable for CookieMutation, SessionCookieAdapter, and JwtProperties. Codebase Memory and direct-source reads supplied caller/implementation evidence instead.
