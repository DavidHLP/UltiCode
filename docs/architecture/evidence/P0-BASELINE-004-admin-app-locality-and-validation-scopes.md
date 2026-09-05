# P0-BASELINE-004 Admin Deep Module and Validation Scope Baseline

> status: FROZEN
> head: c344f6268084a893f0bde871da21e5130a331207
> scope: `services/admin/`, `services/app/`, `scripts/dev/`
> evidence: Repository Implemented

## 1. Admin Module deep-module status

### DefaultAdminUserDetailQuery (single-user detail)

`services/admin/src/main/java/com/ulticode/modules/admin/query/DefaultAdminUserDetailQuery.java`
(lines 164-253 per P0-BASELINE-003). Deep module with:
- `AuthAccountDTO` from `AuthorizationSnapshotService` (Auth)
- `UserProfileDTO` from App profile batch
- `SubmissionUserDetailStatsPort` snapshot from Submission (≤5 logical RPCs,
  ≤2 serial rounds per P3-ADMIN-001 amendment)
- Typed `OK/PARTIAL/UNAVAILABLE` degradation via `DegradationStatus`

### AdminUserEnricher (list enrichment)

`services/admin/src/main/java/com/ulticode/modules/admin/projection/AdminUserEnricher.java`
(lines 208-361 per P0-BASELINE-003):
- `enrich(Set<String>)` and `enrichWithStatus(Set<String>)` — batch paths
- `enrichBatchesInParallel` (line 296) — submits Auth identity + App profile
  queries in parallel, waits up to `QUERY_TIMEOUT_MS` (800ms)
- `enrichOne(String)` — still called serially by projections that need email
  (one-row path, no batch alternative)
- Returns `EnrichedUsers` record with `Map<String, AdminUserSummary>`
  + `DegradationStatus`

### DefaultAdminContestProjection (list N+1)

`services/admin/src/main/java/com/ulticode/modules/admin/projection/DefaultAdminContestProjection.java:31-84`:
- `getContests()` (line 38-58): one App `selectPage` call +
  `toAdminVO(contest)` per row
- `toAdminVO()` (line 76-108): calls
  `contestAdminReadPort.countProblemsByContestId(contest.getId())` per row
  — **confirmed N+1**: `countProblemsByContestId` inside per-row mapping
- `getContest()` (line 62-73): single contest read — no N+1

### DefaultAdminProblemListProjection (list N+1)

`services/admin/src/main/java/com/ulticode/modules/admin/projection/DefaultAdminProblemListProjection.java:51-114`:
- `findAdminLists()` (line 52-70): one App search call +
  `enrichAuthor()` per row — `enrichAuthor` calls `userEnricher.enrichOne()`
  per row, which is one-row-only (batch `enrich(Set)` exists but is not used
  in this projection path)
- `getAdminListDetail()` (line 78-101): single detail read + one
  `enrichOne` — no N+1 (detail view)

### Admin Dashboard

`services/admin/src/main/java/com/ulticode/modules/admin/port/adapter/DefaultAdminDashboardReadAdapter.java:78-100`
(Dashboard stats — 800ms parallel 3-owner fanout), `165-243` (user trend —
serial Auth pages of 100, unbounded).

### Admin Dubbo references count

`grep -rn "@DubboReference" services/admin/src/main/java` = **61**
- backend-app: **33**
- backend-auth: **18**
- backend-submission: **6**
- backend-notification: **4** (via `NotificationServiceContract.DUBBO_GROUP`)
- backend-judge: **0** (confirmed via `grep -rn "backend-judge" services/admin` = 0)

## 2. App Module deep-module status

Per P2-APP-005 (already COMPLETE): `backend-problem-domain` and
`backend-moderation-domain` library modules provide pure domain services
`ProblemAdministrationDomainService` and `ContentModerationDomainService`.
`app-web` `AppDomainServiceConfig` registers them as Spring beans. Old
pass-through implementations deleted.

App private modules (from `services/app/pom.xml`): problem, contest,
submission, moderation. Main impl still in `app-web/src/main/java/com/
ulticode/modules/**`.

## 3. Validation scope matrix (current test.sh)

From `scripts/dev/test.sh:19-43`:

| Mode | Infrastructure | Frontend | Backend | Extras |
|---|---|---|---|---|
| static | none (no Docker/DB/services/Testcontainers) | none | source/config contracts only | shellcheck, theme, migration-preflight, coverage wiring |
| unit | none (deny env) | existing node_modules only | -Punit profile: no Docker/DB/Redis/Nacos/Meili, *IT/*IntegrationTest excluded | type-check, lint:check, unit tests |
| quick | same as static + unit | existing node_modules only | same -Punit backend gate | (deprecated alias) |
| full-local | MySQL + Redis Compose | pnpm install allowed | Maven verify | migration and coverage |
| full | MySQL + Redis Compose | pnpm install allowed | Maven verify | build, audit, i18n |
| integration | MySQL + Redis + sandbox/Testcontainers | pnpm install allowed | Maven verify + *IT | migration safety drill |
| core | none for `test.sh core`; named `core` scope may start MySQL/Redis/Nacos/Meili for independent Judge | none | parent/config/readiness smoke with contexts disabled | Core profile contract; enabled-owner wiring and business journey not proven |

### Zero-infrastructure validation boundary

`scripts/test/zero-infra-validation-contract.sh`:
- Static mode runs under deny-PATH (no docker/mysql/redis-cli/curl)
- Static-safe children: devstack-manifest, app-judge-runtime,
  submission-compatibility-retirement, ssh-host-identity, nacos-security,
  submission-backfill, supply-chain, deployment-integrity,
  owner-migration-manifest, api-contract-boundary, dubbo-provider-reference,
  docs-contract, devlite-minimal, core-profile-contract, devstack-control
- Dynamic children skipped in static: redis-acl, audit-owner-boundary,
  owner-schema-contraction, admin-audit-stream, stream-resilience,
  scale-topology, ha-profile, dubbo-mtls, network-reachability,
  judge-sandbox, owner-backup-restore, observability, scheduler,
  fenced-lease, graceful-drain, dependency-resilience, tls-profile,
  redis-acl-rotation
- Unit mode must also pass under deny-PATH: no Testcontainers, no *IT classes

### Core profile contract

`scripts/test/core-profile-contract.sh` validates:
- Core is explicit assembly with a bounded lifecycle loader, not class/resource
  isolation
- the registry allowlist enables Auth/Admin and disables the other four modules
- parent readiness is the only Core HTTP surface
- no Core-specific runtime properties bypass boundary validation
## 4. Validation tooling

### -Punit Maven profile

`services/pom.xml` defines `<id>unit</id>` profile that:
- Excludes `*IT` and `*IntegrationTest` by naming convention
- Sets `Testcontainers` tests to no-op
- Runs under deny-environment (no DB/Redis/Nacos reachable)

Unit mode must pass with `SPRING_PROFILES_ACTIVE`, `DB_USER`,
`DB_PASSWORD`, `DB_HOST`, `DB_PORT`, `REDIS_*` all unset.

### Architecture contract

`scripts/dev/architecture-contract-test.sh`:
- Runs as parent to `test.sh static`
- Invokes child contracts: api-contract-boundary, dubbo-provider-reference,
  docs-contract, core-profile-contract, devstack-control, etc.
- Static-only mode (`ULTI_STATIC_ONLY=1`) skips Docker/network/Maven-test
  children

## 5. Evidence Level

Repository Implemented + Disposable Validatable. All facts source-anchored.
No production SLO or traffic claim.

## Verification

- `git diff --check` must pass
- `bash scripts/test/zero-infra-validation-contract.sh` (disposable, deny-PATH)
- `bash scripts/dev/test.sh static` (static-only mode)
- `bash scripts/dev/test.sh unit` (zero-infra unit gate)