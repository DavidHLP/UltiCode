# Architecture remediation traceability

Status source: [`.auto-flow/TASKS.yaml`](../../.auto-flow/TASKS.yaml), block `architecture_remediation_20260830`.

Canonical Services issue lifecycle: [`services/docs/SERVICES_ISSUES.md`](../../services/docs/SERVICES_ISSUES.md). This document does not replace that registry. It maps the 2026-08-30 architecture assessment and remediation directive to executable repository tasks.

## Status semantics

- `Repository Implemented`: code/configuration/migrations/automation exist in this repository.
- `Locally Validated`: exact local command evidence exists.
- `Staging Validated`: an authorized staging environment was exercised.
- `Production Applied`: an authorized production operation was actually executed.
- `External Execution Required`: repository work may be complete, but environment/authority evidence is absent.

No staging or production application is assumed by repository completion.

## Context and delivery

| Finding or requirement | Task IDs | Primary implementation surfaces | Acceptance evidence | Commit | Status |
| --- | --- | --- | --- | --- | --- |
| Rebuild current Git, topology, task, build, Compose and Docker baseline | `CTX-001` | `.auto-flow/*`, evidence logs, Maven reactor, Compose | Static gates and clean Maven compile/test/verify exit 0; Docker daemon is `BLOCKED_EXTERNAL` with exact permission evidence | `fa71f276e530` | Locally Validated |
| Map every assessment finding and directive item | `TRACE-001` | this file, `.auto-flow/TASKS.yaml`, `SERVICES_ISSUES.md` | 42/42 task mapping, six explicit ADR deferrals, documentation contract exit 0 | `fa71f276e530` | Locally Validated |

## P0 security

| Original finding | Task IDs | Implementation surfaces | Required validation | Commit | Status |
| --- | --- | --- | --- | --- | --- |
| Authentication cookies are emitted with `Secure=false`; configured SameSite is not applied; clear attributes can drift | `P0-SEC-001` | Auth JWT/session/OAuth cookie policy and configuration | Actual login/refresh/logout headers preserve Secure, HttpOnly, SameSite, Path, Domain, and lifetime; non-local and mixed production profiles reject insecure startup; focused 29/29 and Auth 240/240 | `ef10d92c7272` | Locally Validated |
| CSRF validation exists only in Auth and does not cover all Cookie-authenticated mutations or refresh/logout without a valid access token | `P0-SEC-002` | shared `CookieCsrfFilter`, four owner chains, Auth session bootstrap, auth-core/http-client | Four-owner matrix 14/14; focused 39/39; owner reactor 2466 tests; auth-core 71, http-client 10, Console 592, Management 425; type/static gates PASS | `8f061dfdfa5c` | Locally Validated |
| App/Admin/Notification use `anyRequest().permitAll()` and route authorization is fail-open | `P0-SEC-003` | three production chains, App public catalog, route matrix, architecture gate | App 12/12, Admin 5/5, Notification 4/4, App boot 6/6; owner reactor 2222 tests; no broad permit-all remains | `2974c28880f4` | Locally Validated |
| Three resource-server JWT/JWKS implementations and four authentication filters have diverged | `P0-SEC-004`, `ARCH-SEC-001` | `platform/web-security`, App/Admin/Notification/Auth | Shared verifier tests and architecture rule forbidding local copies | pending | TODO |
| Notification rejects the production HTTP JWKS URI; App/Admin omit equivalent URI/JWK hardening | `P0-SEC-004`, `P2-TLS-001` | shared JWKS source/cache, production TLS/JWKS config | Production HTTP JWKS rejection, HTTPS/static-JWKS success, key rotation and outage tests | pending | TODO |
| Shared HS256 internal delegation secret permits cross-service forgery | `P0-SEC-005` | common delegation contract, Admin signer, Owner verifiers, secret injection | issuer/audience/scope/jti/expiry/replay/wrong-key tests; no HMAC fallback | pending | TODO |
| Redis business users receive broad `+@write`; health user receives `+@all`; key patterns do not constrain database-wide commands | `P0-SEC-006` | `docker/redis/generate-users-acl.sh`, ACL policy/tests | Allowed command success; `FLUSH*`, CONFIG, SHUTDOWN, cross-key denial | pending | TODO |
| Redis ACL is a tracked, locally rewritten secret-derived file; production materialization and overlap rotation are absent | `P2-REDIS-001` | runtime ACL directory, generator, rotation runbook, Compose, host deploy | drift check, no tracked verifier, dual-credential rotation/rollback rehearsal | `e15c34c` | Locally Validated; production secret-store rollout external |
| Deployment disables SSH host verification | `P0-SEC-007` | host-deploy action/workflows | forbidden-pattern and workflow tests; required environment `known_hosts` | pending | TODO |
| Nacos credentials are shared and production defaults to standalone-capable configuration | `P0-SEC-008` | Compose and service registry configuration | per-service required credential variables; production standalone rejection | pending | TODO |
| Dubbo/Nacos discovery does not provide trusted workload identity; sensitive reads trust caller-supplied identifiers | `P3-IDENTITY-001`, `P3-NET-001` | Dubbo TLS/identity filters, caller policies, Compose networks | wrong/missing/expired cert and unauthorized service tests | pending | TODO |
| Judge defaults to the host Docker socket | `P3-JUDGE-001`, `P3-NET-001` | Judge Compose/runtime Adapter and runbook | prod forbidden-socket gate; isolated TLS runtime tests | pending | TODO |

## P1 Owner cutovers and data ownership

| Original finding | Task IDs | Implementation surfaces | Required validation | Commit | Status |
| --- | --- | --- | --- | --- | --- |
| App-local and Submission-owner writers coexist; single-writer is runtime configuration rather than source structure | `P1-SUB-001` | App Submission Module, Submission Owner, routing, contracts | duplicate-writer architecture gate; owner tests; App no local writer | pending | TODO |
| The two `DefaultSubmissionWritePort` implementations contain twenty semantic difference blocks | `P1-SUB-001` | writer behavior matrix and contract tests | every difference has a recorded decision and behavior test | pending | TODO |
| Admin rejudge still targets App compatibility | `P1-SUB-002` | Admin Adapter, Submission administration provider, App compatibility provider | duplicate/concurrent/idempotent/authorization rejudge tests and provider inventory | pending | TODO |
| Submission backfill/cutover evidence requires resumable, idempotent, zero-unexplained-diff tooling | `P1-SUB-003` | owner migration/backfill/runbook scripts | dry-run/batch/checkpoint/resume/retry/checksum/failure export rehearsal | pending | TODO |
| Nightly reconciliation scans `app.submissions` instead of Submission-owner facts | `P1-SUB-004` | Submission facts contract/provider, Admin reconciliation, App legacy mapper | owner-facts integration; no legacy SQL; full/incremental and multi-runner tests | pending | TODO |
| Notification ownership is incomplete in reconciliation and compatibility persistence/read paths | `P1-NOT-001` | Notification Owner, App intents, Notification facts, Admin reconciliation | single-writer, read/delivery/retry state and zero-diff tests | pending | TODO |
| Nightly reconciliation scans `app.notifications` | `P1-NOT-001` | Notification reconciliation provider and Admin aggregator | no App notification SQL; owner unavailable/partial behavior | pending | TODO |
| Legacy tables, fields and compatibility contracts must be retired only after expand/migrate/verify/cutover proof | `P1-DATA-001` | new forward migrations, contracts, App/Owner code | fresh/upgrade/compatibility/negative reference gates | pending | TODO |
| Auth/App write `admin.audit_outbox` across Owner schemas | `P1-AUDIT-001` | owner-local outboxes, integration stream, Admin inbox, grant migration | duplicate/disorder/retry/DLQ tests and no cross-schema write/grant gate | f223b88 | DONE |
| Pass-through, mock-only, unused and migration-only seams remain | `P1-SEAM-001`, `ARCH-DUBBO-001` | API modules, providers, references and adapters | caller/provider inventory and architecture rules | efc12eb | DONE for P1-SEAM scope; ARCH-DUBBO remains |
| `app-api` owns 78/106 interfaces and mixes multiple Owner/internal/migration contracts | `ARCH-CONTRACT-001`, `ARCH-DUBBO-001` | API modules/POMs/contract tests | no cycles; owner/consumer/transport/lifecycle classification; japicmp | pending | TODO |

## P2 production control plane

| Original finding | Task IDs | Implementation surfaces | Required validation | Commit | Status |
| --- | --- | --- | --- | --- | --- |
| Production CD does not execute the Owner migration manifest | `P2-MIG-001` | host-deploy, owner migration scripts/configs, post-owner controls, baseline tooling | fresh/upgrade/checksum/dependency/wrong-owner/schema/partial/retry/concurrency tests | `3f204c1`, `c1ef9d0`, `a5a0008` | Locally Validated; production execution external |
| Backup covers only Admin datasource; full five-Owner restore is unproven | `P2-BACKUP-001` | owner backup/restore runbook, Admin backup compatibility surface, Compose volume/docs | five-owner encrypted backup integrity and temporary restore drill with migration validation/checksums/reconciliation/smoke/RPO/RTO | `4423fae` | Locally Validated; production storage/key/restore external |
| Production edge TLS/HSTS and JWKS/cookie transport are not an executable profile | `P2-TLS-001` | Nginx TLS overlays, Compose certificate mounts/ports/healthchecks, Auth production cookie/CORS, resource-owner JWKS | HTTPS-only/HSTS/Secure-cookie/JWKS consistency tests with temporary certificate | `bb01971` | Locally Validated; real certificate/edge authority external |
| Production images permit `latest`; tag-to-digest, signature, provenance and SBOM/scan gates are absent | `P2-SC-001`, `P2-DEPLOY-001` | Dockerfiles, Compose and GitHub workflows/actions | immutable action/image checks, build, SBOM, scan, signature/provenance verification | `80326f1` | Locally Validated; registry/production authority external |
| External GitHub Actions use mutable major tags | `P2-SC-001` | `.github/workflows` | full-SHA architecture gate and YAML parse | `80326f1` | Locally Validated |
| Instrumentation exists but Collector/scrape/dashboard/routing/SLO operation is incomplete | `P2-OBS-001` | observability Compose/config, metrics, dashboards, alerts, runbooks | config validation, metric/alert contract tests and local smoke when runtime exists | `7320923` | Locally Validated; production telemetry/threshold authority external |
| Deploy/rollback/config state is not fully artifact/schema/digest traceable | `P2-DEPLOY-001` | deploy/health/rollback actions, release manifest | config fail-closed, smoke, incompatible rollback denial, partial-system reporting tests | `60784a5` | Locally Validated; remote/production execution external |

## P3 resilience and scale

| Original finding | Task IDs | Implementation surfaces | Required validation | Commit | Status |
| --- | --- | --- | --- | --- | --- |
| Submission recovery tasks share one scheduler; Search consume can starve heartbeat; Admin maintenance can starve audit dispatch | `P3-SCHED-001` | Submission/Search/Admin scheduler configuration and metrics | blocking/saturation/rejection/independent-progress tests | `5a578a7` | Locally Validated; production saturation/drain external |
| Backup, reconciliation, migration and singleton jobs can run on every replica | `P3-LEASE-001` | shared `FencedLease`, Admin `fenced_job_leases` CAS, runbook lease library and job callers | two-runner, expiry, pause, partition, crash, clock-skew, lost-lease stale completion tests | `d5f9866` | DONE |
| HTTP/RPC/workers lack complete graceful drain | `P3-GRACE-001` | common `DrainGate`, Spring `ContextClosedEvent` worker gates, scheduler await-termination, Docker/Compose/PM2 budgets, readiness marker cleanup | real child-JVM SIGTERM integration plus no-new-claim worker tests and existing PEL/row-lease/CAS recovery contracts | `aa42a66` | DONE |
| Timeout/retry policy lacks circuit, bulkhead and total retry budget | `P3-RES-001` | common `DependencyGuard`, Dubbo cluster-filter SPI, RpcPolicy totals, bounded JWKS/OAuth/S3/MeiliSearch clients, fail-closed ban check | timeout/refusal/slow/open/half-open/recovery/saturation and RpcPolicy architecture tests | `e666ab5` | DONE |
| Streams reliability mechanisms require full crash/replay/shutdown/schema proof | `P3-STREAM-001` | Streams adapters, Inbox, Judge/Search/Notification/App/Submission | PEL/ACK/claim/DLQ/poison/crash/duplicate/replay tests | `4ebb418` | DONE; real Redis crash/reclaim runtime is BLOCKED_EXTERNAL when `REDIS_HOST` is unset |
| Fixed container names and local assumptions block two-instance operation | `P3-SCALE-001` | Compose, service discovery, health and state | two-instance registration/distribution/removal/rolling restart test | pending | TODO |
| Stateful components are single points; truthful HA/failover profiles are absent | `P3-HA-001` | MySQL/Redis/Nacos/MeiliSearch reference profiles and runbooks | config validation and permitted failover/reconnect/brain-split drill | pending | TODO |
| Network reachability is broader than the call graph | `P3-NET-001` | Compose networks and static/runtime tests | required-path success and forbidden-path denial | pending | TODO |

## Architecture, testing, review and closure

| Requirement | Task IDs | Evidence | Commit | Status |
| --- | --- | --- | --- | --- |
| Remove duplicate security implementations and prevent recurrence | `ARCH-SEC-001` | Security ArchUnit and forbidden-pattern gate | pending | TODO |
| Establish nonzero, non-regressing JaCoCo gates and prove the gate fails negatively | `TEST-COV-001` | `clean verify`, reports, negative fixture | pending | TODO |
| First complete Standards/Spec/Security review and fix all confirmed findings | `REVIEW-001` | fixed point `8b4012b3d...`, separate review records and reruns | pending | TODO |
| Second independent final review | `REVIEW-002` | reread final implementation; confirmed findings zero | pending | TODO |
| Align tasks, commits, evidence, Git and final report | `CLOSURE-001` | final task/YAML/docs/Git/verification checks | pending | TODO |

## Explicitly deferred architecture expansion

The governing ADR is [`adr/0001-deferred-platform-expansion.md`](adr/0001-deferred-platform-expansion.md).

The following are deliberate non-implementations, not missing tasks:

- Kubernetes.
- Service Mesh.
- Kafka.
- Seata.
- Further decomposition of the App business service.
- Five independent database clusters.

Their re-evaluation triggers, alternatives and migration costs are recorded in the ADR.
