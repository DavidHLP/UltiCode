# P0-BASELINE-001 Baseline Evidence Manifest

> status: FROZEN
> frozen_at: 2026-09-02
> head: c344f6268084a893f0bde871da21e5130a331207
> investigation_commit: 6f97e6d5fee65e3ecf1cbc4e086336dd870606d5
> persistence_head: 55b541bf82f7c060ae7eec236b42fc8e0c496b47
> branch: fix/architecture-remediation
> plan: docs/architecture/plans/ulticode-architecture-followup-plan.md
> tasks: .agent/tasks/ulticode-architecture-followup/TASKS.yaml (43 tasks, P0-P6, DAG acyclic)

## Fact Priority (code > tests/gates > POM/Compose/scripts > current-status > ADR > SERVICES_ISSUES > historical)

## Frozen Facts (source-anchored)

### CROSS / Owner-Worker Topology
- Five Data Owners (Auth, Admin, App, Submission, Notification) + two Workers (Judge, Search). Source: `AGENTS.md:14`, `services/docs/SERVICES_ISSUES.md:14-18`, `docs/project/current-status.md:9-12`.
- Judge independent Worker, reuses storage-free `backend-judge-runtime` plus owner APIs, no HTTP/business tables, Redis Streams consumer + Docker sandbox, Dubbo remote adapters. Source: `AGENTS.md:14`, `services/judge/pom.xml`, `services/judge/src/main/java/**`.
- Submission mutation owner-only via `services/submission/` (actual path, not `services/backend-submission/`). Evidence: `services/submission/src/main/java/com/ulticode/submission/dubbo/provider/SubmissionIntakeProvider.java`, `services/app/app-web/src/main/java/com/ulticode/modules/submission/port/adapter/RemoteSubmissionWritePort.java`, `services/docs/SERVICES_ISSUES.md:SVC-003`.
- App has no local writer; only `RemoteSubmissionWritePort` -> Dubbo. Gate: `services/app/app-web/src/test/java/com/ulticode/modules/submission/port/SubmissionPortWiringTest.java`.
- Kubernetes/Kafka/Service Mesh/Seata non-adoption is NOT defect (explicit exclusion). Source: `docs/architecture/decisions/0001-deferred-platform-expansion.md`, `plan excluded_scope`.

### AREA-INFRA
- Default MySQL/Redis/MeiliSearch share single-host failure domain; HA compose does not declare transparent failover. Source: `docker-compose.yml`, `docker-compose.ha.yml:10-17`, `docker-compose.prod.yml:46-66`, `services/docs/SERVICES_ISSUES.md:68-72` (now DEFERRED SVC-007).
- Redis: Streams, cache, rate-limit, replay, queue, judge, Pub/Sub share instance; ACL isolates identity/keyspace only, not memory/eviction/connection/failure. Source: `docker/redis/generate-users-acl.sh:58-74`, `services/**/application*.yml`.
- MySQL Owner schema/account isolated; encrypted backup, checksum, Flyway metadata, disposable restore drill exist. Source: `docs/operations/backup-and-recovery.md:7-23`, `init-db/migrations/*`, `scripts/dev/migrate-owner-*.sh`.
- Search worker sole MeiliSearch writer; App indexed read has explicit DB fallback. Source: `services/search/**/SearchDocumentIndexWorker.java:40-55`, `services/app/**/DefaultSearchReadProjection.java:91-148`.

### AREA-APP
- One `service.version.app` releases four private modules + `app-web`. Source: `services/app/pom.xml:16-29`.
- Private modules shallow, main impl in `app-web/src/main/java/com/ulticode/modules/**`. Source: `services/app/modules/**`, `services/app/app-web/src/main/java/com/ulticode/modules/**`.
- `app-api` 75 interfaces snapshot; at least 4 misplaced ownership (`JudgeConfigPort`, `JudgeEnqueuePort`, `VerdictResolvePort`, `ModerationUserReadPort`). Source: `services/api/app-api/src/main/java/**`.
- Counts (files/LOC/interfaces/annotations) are baseline hints, not split justification.

### AREA-ADMIN
- 61 Dubbo references: App33 Auth18 Submission6 Notification4, no Judge. Source: `services/admin/src/main/java/**/@DubboReference` (inventory via grep).
- `AdminUserEnricher` batch merges Auth/App, returns OK/PARTIAL/UNAVAILABLE; two batches currently serial. Source: `services/admin/src/main/java/com/ulticode/modules/admin/projection/AdminUserEnricher.java:208-361`.
- Dashboard stats parallel 3 owners with 800ms; user trend pages Auth 100/page serial. Source: `services/admin/src/main/java/com/ulticode/modules/admin/port/adapter/DefaultAdminDashboardReadAdapter.java:78-100,165-243`.
- RPC: Query 800ms/1 retry, total budget 1.6s, bulkhead 32, 5 failures open 30s. Source: `services/api/rpc-resilience/src/main/java/com/ulticode/common/rpc/RpcPolicy.java:74-100`.

### AREA-LEGACY
- App mutation always to Submission Owner via `RemoteSubmissionWritePort`. Source: `services/app/app-web/src/main/java/**/RemoteSubmissionWritePort.java`.
- `legacy-rollback` explicitly enabled via DevStack, opens local read/Judge compatibility. Source: `scripts/dev/devstack-manifest.sh:195-202`, `services/app/app-web/src/main/java/com/ulticode/app/config/AppJudgeCompatibilityConfiguration.java`.
- App Submission snapshot 59 Java files, 18 legacy-conditioned; Mapper assembled via legacy scan. Source: `services/app/app-web/src/main/java/com/ulticode/app/config/LegacySubmissionMapperScanConfig.java`, `services/app/app-web/src/main/java/com/ulticode/app/config/SubmissionRoutingProperties.java`.
- Normal-path still uses runtime UUID/push alias/status codec. Source: `services/app/app-web/pom.xml:68` + imports (`AppUuidGenerator`, `SubmissionResultPushPort`, `SubmissionStatusCodec`).
- Graph generation may be stale / returned deleted symbols; all negative/exhaustive claims must be source-re-read. Source: `check_index_coverage` disclosure.

## Drift Register (current vs historical/stale)

| Item | Current | Historical/Stale | Action |
|------|---------|------------------|--------|
| Judge topology | `AGENTS.md:14` independent Worker | older docs describing Judge reusing app-web | Updated, P0-001 freezes current |
| Submission path | `services/submission/` | `services/backend-submission/` | Canonical is `services/submission/` |
| SERVICES_ISSUES OPEN | `OPEN: none` (CLOSED SVC-003, DEFERRED SVC-006..010) | prior OPEN findings | Use current registry only |
| Platform expansion | ADR-0001 DEFERRED (no K8s/Kafka/mesh/Seata) | - | Not defect |
| Graph freshness | `graphify` / `codebase-memory` may be stale | - | Direct source read is authoritative |

## Verification

- `git rev-parse HEAD` => c344f6268084a893f0bde871da21e5130a331207 (clean worktree before manifest creation)
- `git status --short` => clean (except this manifest, which is expected)
- Paths exist: `services/docs/SERVICES_ISSUES.md`, `docs/project/current-status.md`, `AGENTS.md`, `services/app/pom.xml`, `services/app/app-web/pom.xml`, `docker/redis/generate-users-acl.sh`, `docker-compose.yml`, `docker-compose.ha.yml`, `docker-compose.prod.yml`
- Acceptance: four areas each have current source+config+tests+Gate evidence bound to commit+path above; no historical archive used as current fact without re-verification.

## Evidence Level

- Repository Implemented + Disposable Validatable (no production evidence claimed; excluded_scope: production-deployment-evidence)

