# Codex continuation handoff — UltiCode architecture remediation

## 0. Purpose

This is the single operational handoff for continuing the current session in Codex. It consolidates the user’s objective, repository rules, decisions, completed work, commits, verification evidence, external blockers, the full 42-task status, and the exact dirty-worktree state where execution stopped.

This is an organized handoff, not a raw transcript. No credential values are included.

## 1. Codex entry procedure

Start here; do not restart discovery from scratch.

1. Open `/home/david/Projects/UltiCode`.
2. Read the already-authoritative rules before editing:
   - `/home/david/.codex/AGENTS.md`
   - `/home/david/Projects/UltiCode/AGENTS.md`
   - `/home/david/Projects/UltiCode/services/AGENTS.md` for backend work
   - the nearest nested `AGENTS.md` for any other subtree touched
3. Preserve the dirty worktree. It is active P1-SUB-004 work, not disposable output.
4. Every shell command must be prefixed with `rtk`.
5. Use Zulu Java 17 for Maven:

   ```bash
   rtk mise exec java@zulu-17.68.203.0 -- ./mvnw ...
   ```

6. Fix the current one-line compilation blocker first, then rerun targeted compile. Do not redesign or revert the current reconciliation work.
7. Continue `.auto-flow/TASKS.yaml` in dependency order. Canonical active task: `P1-SUB-004`.
8. Make local Conventional Commits only. Do not push.
9. Do not execute production, remote-host, credential-rotation, account-provisioning, migration, deployment, sudo, group-membership, or other external mutations.
10. After code changes, run `rtk graphify update .`.

## 2. User objective and authority

The user authorized complete repository implementation of the architecture-remediation program across P0–P3, including:

- code, tests, migrations, scripts, CI/CD controls, runbooks, documentation, local commits, and evidence;
- clean ownership cutovers, removal of obsolete compatibility paths, and contract alignment;
- truthful external blockers where production/runtime authority or infrastructure is unavailable;
- all 42 remediation tasks, not a selected subset;
- two final review passes, full verification, closure evidence, and a final report.

Explicitly not authorized:

- push, merge, publish, or history rewrite;
- production data mutation or production migration;
- production deployment, rollback, traffic switch, or credential rotation;
- remote-host mutation;
- sudo, Docker-group changes, account changes, or secret-store changes;
- fabricating production observation, HA, failover, traffic, SLO, or cutover evidence.

Repository work may make production actions executable and verifiable, but must label the real execution as external when it was not run.

## 3. Repository and environment state

- Repository: `/home/david/Projects/UltiCode`
- Branch: `fix/architecture-remediation`
- Baseline: `main@8b4012b3d13678eaec38a82980c8e3558123b5a8`
- Last committed checkpoint before current dirty WIP:
  - `28563d0eeffadcf2f80915b7b68c2c178156c139`
  - `chore(checkpoint): close Submission backfill task`
- No staged changes at handoff creation time.
- Dirty worktree contains active P1-SUB-004 implementation.
- `git diff --check` currently passes.
- OS: Arch Linux, x86_64.
- Working Java: Zulu `17.0.20.1` via mise.
- Avoid the obsolete local Java `17.0.2`; it fails in JVM cgroup-v2 processor discovery before application assertions.
- Docker CLI and Compose exist, but daemon access is blocked:
  - current UID is not in group `docker`;
  - `/var/run/docker.sock` is `root:docker` mode `0660`;
  - no native Redis is installed;
  - Docker/Testcontainers runtime checks are `BLOCKED_EXTERNAL`, never PASS.
- Java/TypeScript LSP references were unavailable in this harness. Codebase Memory, graph tools, and direct source were used as fallback.
- Codebase Memory project: `UltiCode`.
- Graphify exists and must be refreshed after changes.

## 4. Required engineering invariants

### Backend and contracts

- Preserve `controller -> service -> mapper -> entity` and current domain ownership.
- Preserve the existing `Result`/`RpcResult` envelopes and field mappings.
- Validate trust-boundary input with typed DTOs.
- Use parameterized SQL/MyBatis annotations.
- Cross-service privileged writes use explicit contracts and signed delegated identity.
- Audit identity comes from authenticated/delegated principals, never request-carried identity.
- Audit writes remain owner-local; full cross-owner audit outbox work is P1-AUDIT-001.
- Access/refresh tokens remain HttpOnly cookies; refresh tokens remain hash-only and database-backed.
- WebSocket auth remains access-cookie-only.
- `/admin/**` and privileged methods require `ADMIN` or `SUPER_ADMIN`.

### Database

- `init-db/migrations/` is the only Flyway source.
- Never edit an applied migration; add a new forward migration.
- Migration names: `V{14-digit timestamp}__Description.sql`.
- Never add usable default users/passwords.
- Preserve the secure refresh-token/seed-lock migration.
- No cross-owner DB joins or writes.

### Git and verification

- Preserve user work; no destructive Git commands.
- Review `git diff` and run `git diff --check` before each checkpoint.
- Do not claim a test or runtime gate passed unless it actually ran and exited successfully.
- Conventional commit subject: `<type>(optional-scope): description`.
- No push.

## 5. Canonical execution/evidence surfaces

- Task graph: `.auto-flow/TASKS.yaml`
- Current handoff state: `.auto-flow/HANDOFF.yaml`
- Resume summary: `.auto-flow/RESUME.md`
- Decisions: `.auto-flow/DECISIONS.md`
- Worklog: `.auto-flow/WORKLOG.md`
- Consolidated evidence: `.auto-flow/EVIDENCE.md`
- Per-task evidence: `.auto-flow/evidence/architecture-remediation-20260830/`
- Architecture documentation: `PROJECT_DOCUMENTATION.md`
- Services issue registry: `services/docs/SERVICES_ISSUES.md`
- Supported verification wrapper:

  ```bash
  rtk ./scripts/dev/test.sh quick
  rtk ./scripts/dev/test.sh full
  rtk ./scripts/dev/test.sh integration
  ```

- Core static gates:

  ```bash
  rtk ./scripts/dev/architecture-contract-test.sh
  rtk bash scripts/dev/docs-contract-test.sh
  rtk bash scripts/dev/migrate-owner-preflight-test.sh
  rtk git diff --check
  ```

## 6. Full 42-task status

Current count: 13 DONE, 29 TODO. `P1-SUB-004` is the active implementation task, although `.auto-flow/TASKS.yaml` still records it as TODO until its implementation/evidence checkpoint is complete.

- `CTX-001`: DONE — Rebuild remediation context and baseline evidence
- `TRACE-001`: DONE — Map every finding to implementation evidence
- `P0-SEC-001`: DONE — Enforce shared secure cookie policy
- `P0-SEC-002`: DONE — Apply cookie-auth CSRF across owners
- `P0-SEC-003`: DONE — Make HTTP route authorization fail closed
- `P0-SEC-004`: DONE — Unify JWT and JWKS resource security
- `P0-SEC-005`: DONE — Replace symmetric internal delegation signing
- `P0-SEC-006`: DONE — Restrict Redis principals by exact commands
- `P0-SEC-007`: DONE — Verify SSH host identity in deployment
- `P0-SEC-008`: DONE — Isolate Nacos service credentials and modes
- `P1-SUB-001`: DONE — Establish Submission owner-only mutation path
- `P1-SUB-002`: DONE — Route Admin rejudge to Submission owner
- `P1-SUB-003`: DONE — Build resumable Submission backfill verification
- `P1-SUB-004`: TODO / ACTIVE WIP — Move Submission reconciliation to owner facts
- `P1-NOT-001`: TODO — Complete Notification owner persistence cutover
- `P1-DATA-001`: TODO — Retire legacy data and compatibility contracts
- `P1-AUDIT-001`: TODO — Remove cross-owner audit database writes
- `P1-SEAM-001`: TODO — Remove shallow and migration-only seams
- `P2-MIG-001`: TODO — Execute owner migration manifest in CD
- `P2-BACKUP-001`: TODO — Back up and restore all data owners
- `P2-REDIS-001`: TODO — Materialize and rotate Redis ACL safely
- `P2-TLS-001`: TODO — Provide production TLS and HSTS profile
- `P2-SC-001`: TODO — Verify immutable signed image supply chain
- `P2-OBS-001`: TODO — Operate metrics traces alerts and SLOs
- `P2-DEPLOY-001`: TODO — Enforce release rollback and config integrity
- `P3-SCHED-001`: TODO — Isolate critical scheduler executors
- `P3-LEASE-001`: TODO — Fence singleton jobs across replicas
- `P3-GRACE-001`: TODO — Drain services safely on termination
- `P3-RES-001`: TODO — Bound retries circuits and dependency concurrency
- `P3-STREAM-001`: TODO — Prove stream crash replay and compatibility
- `P3-SCALE-001`: TODO — Validate two-instance service operation
- `P3-HA-001`: TODO — Provide truthful stateful HA profiles
- `P3-IDENTITY-001`: TODO — Authenticate Dubbo workloads with mTLS
- `P3-NET-001`: TODO — Restrict service network reachability
- `P3-JUDGE-001`: TODO — Remove production Docker socket trust
- `ARCH-CONTRACT-001`: TODO — Align contracts with bounded owners
- `ARCH-DUBBO-001`: TODO — Prune provider and reference sprawl
- `ARCH-SEC-001`: TODO — Forbid duplicate security implementations
- `TEST-COV-001`: TODO — Enforce real non-regression coverage gates
- `REVIEW-001`: TODO — Run first full standards and spec review
- `REVIEW-002`: TODO — Run independent final implementation review
- `CLOSURE-001`: TODO — Close tasks commits evidence and final report

For exact dependencies, acceptance criteria, validation commands, and external notes, use the same task entries in `.auto-flow/TASKS.yaml`; do not invent alternate task IDs or a second task system.

## 7. High-level internal execution checklist

The session’s broader execution checklist has two completed categories and seven open categories:

- DONE — Persist task graph and baseline evidence
- DONE — Unify HTTP and internal security modules
- IN PROGRESS — Complete Submission, Notification, and Audit cutovers
- PENDING — Automate production migration, backup, and Redis materialization
- PENDING — Harden supply chain, observability, and release controls
- PENDING — Implement scheduler resilience, Streams handling, and graceful shutdown
- PENDING — Implement multi-instance identity/network controls and Judge isolation
- PENDING — Govern contracts, architecture docs, and coverage gates
- PENDING — Complete two reviews, full verification, commits, and closure

## 8. Completed work by task

### CTX-001 / TRACE-001

- Persisted the 42-task DAG and baseline evidence.
- Captured Git, Maven, Compose, migration, architecture, documentation, Docker, coverage, and graph baselines.
- Created the remediation branch before implementation.
- Local baseline compile/test/verify passed before remediation work.

### P0-SEC-001 — secure cookie policy

- Reproduced missing `Secure` and weak `SameSite` behavior.
- Added startup rejection for insecure production policy, including mixed-profile bypass prevention.
- Emits complete Spring `ResponseCookie` policy.
- `Secure=false` is allowed only when every active profile is local (`dev`/`test`/`ci`).
- Focused 29/29 and Auth 240/240 passed.
- GREEN commit: `ef10d92c7`.

### P0-SEC-002 — cross-owner CSRF

- Reproduced refresh-cookie bypass, bearer-only over-blocking, and header/cookie mismatch acceptance.
- Added one shared stateless constant-time double-submit filter across Auth, App, Admin, and Notification.
- Removed the Auth-only Redis validator/state path.
- Frontend refresh behavior sends the CSRF header and falls back to the readable cookie after hard reload.
- Owner reactor and frontend type-check/test gates passed.
- GREEN commit: `8f061dfdf`.

### P0-SEC-003 — fail-closed route authorization

- Removed broad `anyRequest().permitAll()` behavior.
- Preserved only explicit public App routes.
- App privileged families require auth/role.
- Admin is health-only public, with route and method defenses.
- Notification authenticates every non-health route.
- Owner route matrices and boot tests passed.
- GREEN commit: `2974c2888`.

### P0-SEC-004 — shared JWT/JWKS boundary

- Centralized access-token claims, filter, HS/RS verifier, bounded JWKS cache, and owner adapters in shared web-security.
- Deleted duplicate owner-local JWT implementations.
- Production contract requires RS256 and HTTPS allowlisted Auth JWKS.
- TLS termination/provisioning remains P2-TLS-001.
- GREEN commit: `828a9417a`.

### P0-SEC-005 — asymmetric delegated identity

- Replaced internal symmetric HS signing with separate 2048-bit RS256 Admin and Bootstrap keys.
- Admin holds private signing keys; Auth/App/Notification/Submission hold public keys only.
- Assertions bind `kid`, issuer, target audience, actor service/type/subject, short lifetime, and one-shot Redis `jti` replay claim.
- Bootstrap actor is separately scoped.
- No production key rotation was performed.
- GREEN commit: `05285c5e4`.

### P0-SEC-006 — exact Redis ACL principals

- Deny-by-default ACLs with explicit command allowlists.
- Owner-specific key/channel patterns.
- Separate PING-only health user.
- Atomic ACL-file replacement.
- Static ACL and Compose checks pass.
- Runtime Redis integration is Docker-blocked.
- GREEN implementation commit in history: `054b95e7d`.

### P0-SEC-007 — SSH host identity

- Reproduced `StrictHostKeyChecking=no` and automatic `ssh-keyscan` trust.
- Deployment, rollback, and health workflows now require environment-pinned `DEPLOY_KNOWN_HOSTS` and strict `BatchMode` SSH.
- No remote host was changed.
- GREEN commit: `8baac1c25`.

### P0-SEC-008 — Nacos workload identities and mode

- Production Nacos is cluster-only with required peer list.
- Development remains explicit standalone in a dev namespace.
- Six distinct service registry users/roles with service/config permissions.
- Explicit application names, credentials, and namespaces in Compose, PM2, and local tooling.
- Built-in Nacos account remains disabled.
- Static/effective Compose, Nacos security, architecture, docs, launcher, generated-env, and graph gates passed.
- Live Nacos/Dubbo registration is Docker-blocked.
- GREEN commit: `b689e73af`.

### P1-SUB-001 — owner-only Submission mutation

- RED evidence found three App `SubmissionIntakePort` implementations.
- Deleted App-local Submission mutation writer/router, fence adapters, judge/result dispatchers, shadow comparator, lease reaper, local result outbox path, and associated tests.
- `RemoteSubmissionWritePort` is the sole App intake implementation and always calls `backend-submission`.
- App `APP_SUBMISSION_ROUTING_MODE` now applies only to temporary read projection routing.
- Submission owner retains local writer, fenced verdicts, lease reaper, judge/result/created outboxes, and direct providers.
- Added an explicit 20-case writer decision matrix in `PROJECT_DOCUMENTATION.md`.
- Affected App/Submission suite: 446 reports, 1490 tests, 0 failures, 0 errors, 13 skipped.
- GREEN commit: `d4a493b92`.
- Evidence: `p1-sub-001-red.result`, `p1-sub-001-green.result`.

### P1-SUB-002 — Admin rejudge to owner

- RED evidence found Admin rejudge bound to `backend-app` with a local Admin fallback.
- Deleted App and Admin rejudge compatibility providers/services/policies/state machine/contracts/tests.
- Removed obsolete `RejudgePolicy` and old mutable rejudge result contract.
- Admin `SubmissionCutoverService` now expresses intent only and calls group `backend-submission`.
- Submission owner now performs:
  - RS256 delegated-identity verification;
  - target audience `backend-submission`;
  - Admin/Super Admin and self-delegation checks;
  - Redis replay claim;
  - durable idempotency receipt claim/replay/conflict handling;
  - terminal generation CAS;
  - Judging lease/attempt invalidation;
  - non-shadow judge outbox creation.
- Added `submission_command_receipt` migration and owner mapper/entity/executor.
- Fixed owner boot scans for the receipt mapper and shared replay guard after the first full suite exposed missing beans.
- Full Admin/Submission suite: 205 reports, 680 tests, 0 failures, 0 errors, 3 skipped.
- GREEN commit: `3a8f9316a`.
- Evidence: `p1-sub-002-red.result`, `p1-sub-002-green.result`.
- Full cross-owner audit outbox is deliberately deferred to P1-AUDIT-001; owner receipts preserve actor/trace/fingerprint/result meanwhile.

### P1-SUB-003 — resumable backfill verification

- Replaced the one-shot implicit full copy with:
  - `backfill --dry-run` by default;
  - explicit `backfill --execute` confirmation and all-writers quiesce gate;
  - per-table primary-key batching;
  - schema-bound local checkpoint;
  - separate dry-run checkpoint;
  - TSV failure export;
  - insert-only target writes;
  - NULL-safe same-key field conflict detection;
  - no overwrite of newer owner rows;
  - count, missing/extra-key, field, checksum, and writer parity verification;
  - cutover that only revokes grants after zero differences and no longer performs an implicit copy.
- Added `scripts/test/submission-backfill-contract.sh` fake-MySQL rehearsal.
- Extended disposable owner migration integration test to run backfill and verify before cutover.
- Fake-MySQL contract, migration preflight, architecture/docs/shell/diff gates, and Graphify passed.
- Disposable MySQL/Redis rehearsal remains Docker-blocked.
- GREEN commit: `73d9f78e2`.
- Evidence: `p1-sub-003-green.result`.

## 9. Commit history for this remediation branch

From baseline `8b4012b3d...` to the last committed checkpoint:

```text
fa71f276e docs(architecture): track remediation program
3d5c7814d test(security): reproduce unsafe cookies
8417bfa74 test(security): reject insecure cookie startup
dad4fafc7 test(security): block mixed-profile cookie bypass
ef10d92c7 fix(security): enforce secure cookie policy
e3f67f36f chore(architecture): checkpoint cookie remediation
6426cfb84 test(security): expose cross-owner csrf gaps
8f061dfdf fix(security): enforce cross-owner csrf
a98904597 chore(architecture): checkpoint csrf remediation
07dd95f23 test(security): expose route authorization gaps
2974c2888 fix(security): fail closed owner routes
6ca06e1da chore(architecture): checkpoint route hardening
7f21ce9d3 test(security): require shared jwt boundary
828a9417a fix(security): unify resource JWT verification
d805c8d95 chore(checkpoint): close JWT remediation
aaa8d5ab5 test(security): expose symmetric delegation signing
05285c5e4 fix(security): sign delegation assertions asymmetrically
061f1aaac chore(checkpoint): close delegation task
054b95e7d fix(security): restrict Redis ACL principals
daa208c96 chore(checkpoint): close Redis ACL task
ac6cbfea9 test(security): expose SSH host trust bypass
8baac1c25 fix(security): pin deployment SSH host identity
b9f1efe29 chore(checkpoint): close SSH identity task
b689e73af fix(security): isolate Nacos service identities
17a297c3d chore(checkpoint): close Nacos security task
c929eb382 test(submission): expose duplicate App writer
a2153b847 fix(security): select delegation verifier constructor
d4a493b92 refactor(submission): enforce owner-only intake
2ef79bae6 test(submission): expose App-owned rejudge
3a8f9316a refactor(submission): move admin rejudge to owner
82cfcf077 chore(checkpoint): close Submission owner tasks
73d9f78e2 feat(migration): add resumable Submission backfill
28563d0ee chore(checkpoint): close Submission backfill task
```

## 10. Important verification evidence already obtained

### Baseline

- Maven clean compile/test/verify passed before remediation.
- Baseline Surefire: 809 reports, 2739 tests, 0 failures, 0 errors, 20 skipped.
- Baseline static architecture/docs/migration/Compose gates passed.

### P0 and P1

- Cookie: focused 29/29; full Auth 240/240.
- CSRF: owner reactor 2466 tests; frontend package/app type-check/test gates passed.
- Routes: owner reactor 2222 tests; route matrices passed.
- Submission owner-only intake: 1490 tests, zero failures/errors.
- Admin/Submission rejudge: 680 tests, zero failures/errors.
- Backfill fake-MySQL contract:
  - dry-run checkpoint resume PASS;
  - no-write behavior PASS;
  - count/checksum/field/writer parity gate PASS;
  - conflict failure export PASS;
  - newer-owner protection PASS;
  - execute confirmation rejection PASS.
- Current architecture gate includes Redis ACL, SSH identity, Nacos security, backfill contract, documentation drift, and ownership assertions.

### Runtime gates that were not run

- Redis runtime integration: Docker-blocked.
- Nacos/Dubbo live registration: Docker-blocked.
- Disposable MySQL/Redis owner migration/backfill rehearsal: Docker-blocked.
- Production traffic/cutover/HA/failover/SLO evidence: external and not fabricated.

## 11. Current dirty worktree — P1-SUB-004

### Task acceptance criteria

From `.auto-flow/TASKS.yaml`:

1. Nightly full/incremental reconciliation consumes Submission-owner bounded facts and is multi-replica safe.
2. No App submissions SQL remains; failures emit metrics and actionable records.
3. Validation must include reconciliation tests and a forbidden-SQL architecture gate.
4. Do not add cross-owner joins.

### Current intended design

The dirty WIP is moving Submission orphan reconciliation away from App:

- New Submission API DTO:
  - `SubmissionUserReferenceCountDTO(accountId, rowCount)`
- New bounded owner contract:
  - `SubmissionReconciliationReadPort.findUserReferenceCounts(afterAccountId, createdSince, limit)`
  - max page size 500
  - `createdSince == null` means full history
  - non-null `createdSince` means incremental creation window
- Submission owner mapper groups `submissions.user_id` locally and pages by account ID.
- Submission owner Dubbo provider validates cursor, page size, ordering, duplicates, nulls, and row counts.
- Admin adapter references group `backend-submission`.
- `OwnerReconciler` is being changed to:
  - use Submission-owner facts for Submission orphan scans;
  - retain App facts only for App-owned children;
  - expose full and incremental entry points;
  - use MySQL `GET_LOCK`/`RELEASE_LOCK` through `ReconciliationRunMapper` for multi-replica exclusion;
  - persist mode and error details in run JSON;
  - increment reconciliation run/failure/skip metrics;
  - return SKIPPED without persisting when another replica holds the lease.
- App `ReconciliationOrphanCounts.submissions` remains a wire-compatible zero placeholder until contract contraction in P1-DATA-001.
- App reconciliation mapper should contain no SQL against `submissions`.
- Architecture gate now rejects App reconciliation `submissions` SQL and requires the new owner adapter/lock/incremental method.

### Dirty worktree files

Modified:

```text
scripts/dev/architecture-contract-test.sh
services/admin/src/main/java/com/ulticode/modules/reconciliation/OwnerReconciler.java
services/admin/src/main/java/com/ulticode/modules/reconciliation/ReconciliationRunMapper.java
services/admin/src/test/java/com/ulticode/modules/reconciliation/OwnerReconcilerIT.java
services/admin/src/test/java/com/ulticode/modules/reconciliation/OwnerReconcilerTest.java
services/api/app-api/src/main/java/com/ulticode/app/api/dto/ReconciliationOrphanCounts.java
services/api/app-api/src/main/java/com/ulticode/app/api/service/AppReconciliationReadPort.java
services/api/submission-api/src/test/java/com/ulticode/submission/api/architecture/SubmissionApiContractShapeTest.java
services/app/app-web/src/main/java/com/ulticode/modules/reconciliation/port/AppReconciliationReadMapper.java
services/app/app-web/src/main/java/com/ulticode/modules/reconciliation/port/DefaultAppReconciliationReadPort.java
services/app/app-web/src/test/java/com/ulticode/modules/reconciliation/port/DefaultAppReconciliationReadPortTest.java
services/submission/src/test/java/com/ulticode/submission/dubbo/provider/SubmissionAdminReadProviderIT.java
services/submission/src/test/java/com/ulticode/submission/provider/SubmissionProviderContractTest.java
```

Untracked active WIP:

```text
services/admin/src/main/java/com/ulticode/modules/reconciliation/port/adapter/DubboSubmissionReconciliationReadAdapter.java
services/api/submission-api/src/main/java/com/ulticode/submission/api/dto/SubmissionUserReferenceCountDTO.java
services/api/submission-api/src/main/java/com/ulticode/submission/api/service/SubmissionReconciliationReadPort.java
services/submission/src/main/java/com/ulticode/modules/submission/mapper/SubmissionReconciliationReadMapper.java
services/submission/src/main/java/com/ulticode/submission/dubbo/provider/SubmissionReconciliationReadProvider.java
services/submission/src/test/java/com/ulticode/submission/dubbo/provider/SubmissionReconciliationReadProviderTest.java
```

No WIP files are staged.

### Current compile status

Latest command:

```bash
cd /home/david/Projects/UltiCode/services
rtk mise exec java@zulu-17.68.203.0 -- ./mvnw \
  -pl admin,submission,app/app-web -am test-compile -B
```

Result: exit 1.

Current first compilation blocker:

```text
services/admin/src/main/java/com/ulticode/modules/reconciliation/OwnerReconciler.java
line around 139:
OrphanDetectionResult cannot be converted to Collection<? extends OrphanDetectionResult>
```

Exact current bug:

```java
orphanResults.addAll(submissionOrphans(createdSince));
```

`submissionOrphans` returns one `OrphanDetectionResult`, so the immediate fix is:

```java
orphanResults.add(submissionOrphans(createdSince));
```

After this one-line fix, rerun the same `test-compile`; additional compilation/test issues may surface because the WIP has not reached a green checkpoint.

Latest compile log:

```text
~/.local/share/rtk/tee/1788089296_mise_exe_4e4091.log
```

### Known WIP follow-ups after compile advances

1. Ensure `OwnerReconciler` has only one scheduled method and valid JSON serialization. The duplicate scheduler was already removed, but verify source after compile.
2. Update `ReconciliationRun` status Javadoc if `SKIPPED` remains a supported in-memory status.
3. Decide and prove the “nightly full/incremental” acceptance precisely:
   - current WIP schedules a nightly full run;
   - it exposes an explicit incremental method with a caller-supplied watermark;
   - if acceptance requires a scheduled incremental run, add a bounded schedule and durable successful watermark instead of inventing an untracked `now-minus-X` heuristic.
4. MySQL advisory locks are connection-scoped. Keep acquire/work/release inside one Spring transaction/SqlSession in production. The current `@Transactional` boundary is intended to ensure this; verify with the real Admin IT when Docker is available.
5. Ensure `OwnerReconcilerIT` direct MyBatis invocation uses one session for lock acquire/release.
6. Ensure failure details are valid JSON and include mode plus actionable exception type/message without exposing secrets.
7. Replace the mocked `MeterRegistry` with `SimpleMeterRegistry` or explicit counter mocks if tests must prove failure/skip metrics.
8. Add/retain tests proving:
   - full Submission owner scan;
   - incremental watermark propagation;
   - bounded pages and monotonic cursor;
   - invalid/duplicate/out-of-order owner facts fail closed;
   - busy advisory lock produces SKIPPED and no persisted run;
   - failed owner RPC persists FAILED with an actionable error detail and increments failure metric;
   - App no longer queries `submissions` for reconciliation;
   - Submission owner integration returns grouped full and incremental facts.
9. Run `git diff --check` before any commit.
10. Update `PROJECT_DOCUMENTATION.md`, `services/docs/SERVICES_ISSUES.md`, `.auto-flow` evidence/status/worklog/decisions, and Graphify only after the implementation and tests are green.

## 12. Exact next execution sequence

Use this order.

### Step A — fix and compile

```bash
cd /home/david/Projects/UltiCode
# Edit OwnerReconciler: addAll(...) -> add(...)
cd services
rtk mise exec java@zulu-17.68.203.0 -- ./mvnw \
  -pl admin,submission,app/app-web -am test-compile -B
```

Completion criterion: test compilation exits 0. Fix each real compiler error at the responsible source; do not suppress or delete the owner-facts behavior.

### Step B — run focused unit/contract tests

Suggested focused command after compile succeeds:

```bash
cd /home/david/Projects/UltiCode/services
rtk mise exec java@zulu-17.68.203.0 -- ./mvnw \
  -pl admin,submission,app/app-web -am \
  -Dtest=OwnerReconcilerTest,DefaultAppReconciliationReadPortTest,SubmissionReconciliationReadProviderTest,SubmissionProviderContractTest,SubmissionApiContractShapeTest \
  -Dsurefire.failIfNoSpecifiedTests=false test -B
```

Completion criterion: all focused tests pass and protect the observable owner-boundary behavior.

### Step C — run integration tests when possible

Relevant ITs:

```bash
cd /home/david/Projects/UltiCode/services
rtk mise exec java@zulu-17.68.203.0 -- ./mvnw \
  -pl admin,submission -am \
  -Dtest=OwnerReconcilerIT,SubmissionAdminReadProviderIT \
  -Dsurefire.failIfNoSpecifiedTests=false test -B
```

If Docker remains unavailable, record these as `BLOCKED_EXTERNAL`; do not call them PASS.

### Step D — static ownership gates

```bash
cd /home/david/Projects/UltiCode
rtk ./scripts/dev/architecture-contract-test.sh
rtk bash scripts/dev/docs-contract-test.sh
rtk git diff --check
```

Completion criterion:

- no App reconciliation SQL against `submissions`;
- Admin adapter targets `backend-submission`;
- owner provider and mapper exist;
- full/incremental method exists;
- multi-replica lease gate exists;
- all existing architecture/docs/backfill/security gates pass.

### Step E — affected-module suite

```bash
cd /home/david/Projects/UltiCode/services
rtk mise exec java@zulu-17.68.203.0 -- ./mvnw \
  -pl admin,submission,app/app-web -am test -B
```

Parse Surefire XML for exact report/test/failure/error/skipped totals before recording evidence.

### Step F — documentation, graph, diff, commit, checkpoint

1. Update `PROJECT_DOCUMENTATION.md` with the full/incremental owner-facts reconciliation route, lease, metrics, and external runtime blocker.
2. Update `services/docs/SERVICES_ISSUES.md` SVC-003 retirement state.
3. Run:

   ```bash
   rtk graphify update .
   rtk git diff --check
   ```

4. Commit implementation with a Conventional Commit, likely:

   ```text
   refactor(submission): move reconciliation to owner facts
   ```

5. Create `.auto-flow/evidence/architecture-remediation-20260830/p1-sub-004-green.result` with exact commands/results and Docker external gates.
6. Mark P1-SUB-004 DONE in `.auto-flow/TASKS.yaml`, add commit/evidence, advance handoff to P1-NOT-001, update evidence/worklog/decisions/resume, and make a checkpoint commit.

## 13. Decisions and tradeoffs already made

### Clean repository cutover, truthful production gate

Repository compatibility writers may be removed after automated proofs; real production execution remains external. Never use repository completion as proof of production application.

### Boring reuse over a parallel architecture

Extend existing `.auto-flow`, owner migration scripts, Streams/Inbox, Worker SLO, Admin aggregation, BackupProcessPort, contracts, and architecture gates. Do not create a second task system or replacement service architecture.

### Submission is the sole mutation owner

App captures request facts and calls Submission. Judge calls Submission verdict/fence contracts. Admin expresses rejudge intent. No App local writer or rejudge provider remains.

### Rejudge notification flag

The boolean remains in the wire contract for compatibility, but the owner transition does not perform notification delivery. Notification/event semantics belong to later Notification/Audit tasks.

### Backfill never overwrites owner rows

Insert-only missing rows. Same-key field conflict fails closed and writes an actionable artifact. Cutover requires zero unexplained differences.

### Reconciliation owner boundary

Submission orphan facts must come from `backend-submission`, not App SQL. App’s old DTO field may remain a zero placeholder until P1-DATA-001 contracts it away safely.

### No speculative infrastructure

Do not introduce Kubernetes, Service Mesh, Kafka, Seata, a new MQ, five independent DB clusters, or another App split. Reuse current Docker Compose, Dubbo/Nacos, Redis Streams, MySQL owner schemas, and existing runtime topology unless a later explicit task requires otherwise.

## 14. External blockers and exact unblock conditions

### Docker/Testcontainers

Current blocker:

```text
current user not in docker group
/var/run/docker.sock owner root:docker, mode 0660
```

Do not mutate group membership or use sudo. Unblock only through an externally authorized re-login/newgrp, rootless Docker, or authorized remote Docker context. Then rerun the specific blocked checks.

### Production/remote evidence

The following remain external until explicitly authorized and available:

- real owner schema backfill/cutover;
- production traffic observation and writer drain;
- Nacos account provisioning and live registration;
- credential rotation;
- HA/failover;
- live trace and SLO reports;
- remote deployment/rollback;
- production TLS provisioning;
- Judge node isolation deployment.

Repository work should supply executable runbooks and fail-closed gates, then record `BLOCKED_EXTERNAL` with exact requirements.

## 15. Historical failures that are already understood

- Java 17.0.2 cgroup-v2 metrics NPE: environment/JVM issue; use Zulu 17.0.20.1.
- First full Submission rejudge suite failure:
  - missing `SubmissionCommandReceiptMapper` scan;
  - missing shared `RedisDelegationAssertionReplayGuard` component scan.
  - Both were fixed; subsequent full suite passed.
- Docker-dependent full suites may fail on unavailable localhost Redis/MySQL/Testcontainers. Do not misclassify as source regression without reading the exact failure.
- A tool log warning or first narrow failure is not a blocker; isolate source regressions from external infrastructure.

## 16. Current verification truth

At this handoff:

- Last committed implementation checkpoint: green for P1-SUB-003 static/fake-DB gates.
- Current P1-SUB-004 dirty WIP: not green.
- Current targeted `test-compile`: FAIL, one known source error at `OwnerReconciler` `addAll` vs `add`.
- Current `git diff --check`: PASS.
- No current WIP tests may be claimed as passing until rerun after compile succeeds.
- Docker-dependent runtime gates remain `BLOCKED_EXTERNAL`.

## 17. Handoff stop condition

The next agent should not stop after merely fixing compilation. P1-SUB-004 is complete only when:

- Submission owner serves bounded full/incremental reconciliation facts;
- Admin consumes those facts with no App `submissions` SQL;
- multi-replica overlap is fenced;
- failures create metrics and actionable persisted records;
- focused tests and affected-module tests pass;
- integration tests either pass or are truthfully recorded as Docker-blocked;
- architecture/docs/diff/Graphify gates pass;
- implementation, evidence, task status, handoff, and local commits are updated together.

Then continue immediately to P1-NOT-001 unless an actual external-only gate prevents repository work.
