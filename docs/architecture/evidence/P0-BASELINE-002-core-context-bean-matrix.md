# P0-BASELINE-002 Core Parent/Child Context, Package Scan, and Bean Registration Matrix

> status: FROZEN
> head: c344f6268084a893f0bde871da21e5130a331207
> scope: `services/core/` — single-process Core profile assembly
> evidence: Repository Implemented

## 1. Core module Maven closure

`services/core/pom.xml` depends on all six Owner implementation JARs + platform:

```
backend-common, backend-observability, backend-web-security,
backend-auth, backend-admin, backend-app-web, backend-submission,
backend-notification, backend-search
+ spring-boot-starter-web, spring-boot-starter-actuator, spring-boot-starter-validation
```

**All Owner JARs share one Core classpath.** This is the root cause of the
child-context isolation blocker (see §5).

## 2. Parent context beans (CoreApplication + CoreAssemblyConfiguration)

`CoreApplicationScan` excludes `DataSourceAutoConfiguration`,
`DataSourceTransactionManagerAutoConfiguration`, `RedisAutoConfiguration`,
`RedisRepositoriesAutoConfiguration`, `MybatisPlusAutoConfiguration`,
`RedissonAutoConfigurationV2`, `RedissonAutoConfigurationV4`, and
`MybatisAutoConfiguration`.

`CoreAssemblyConfiguration` imports `CoreOwnerDataSourceConfiguration` which
defines in the **parent** context:
- 5 `DataSourceProperties` beans (auth, admin, app, submission, notification)
- 5 `HikariDataSource` beans (auth, admin, app, submission, notification)
- 5 `PlatformTransactionManager` beans (auth, admin, app, submission, notification)
- 5 `SqlSessionFactory` beans (auth, admin, app, submission, notification)

`CoreLifecycleConfiguration` defines `DrainGate` and `CoreDrainListener`.

`CoreSecurityConfiguration` defines a `SecurityFilterChain` that permits
`/api/v1/core/health/ready` and denies all else.

`CoreReadinessService` and `CoreReadinessController` are in the parent.

`CoreOwnerContextManager` and `CoreModuleRegistry` are in the parent.

`CoreLocalAuthorizationMutationAdapter` implements
`AuthorizationMutationService` in the **parent** context (the only local
adapter).

## 3. Per-child assembly (CoreOwnerBootConfigurations)

Six nested `@Configuration` classes, each a standalone
`SpringApplicationBuilder(module.bootConfiguration())` invocation with
`WebApplicationType.NONE`. **No `.parent(...)` or hierarchical child-context
flag is passed** — each child is a new root Spring Boot context, not a
Spring-Framework child of the Core parent.

### ComponentScan / MapperScan matrix

| Child    | @ComponentScan basePackages                                                                 | @MapperScan (sqlSessionFactoryRef)                    | @Import                                      | @EnableAutoConfiguration excludes                         |
|----------|-----------------------------------------------------------------------------------------------|-------------------------------------------------------|----------------------------------------------|-----------------------------------------------------------|
| Auth     | `com.ulticode.auth`, `com.ulticode.common`, `com.ulticode.websecurity`                        | 7 auth mapper packages                                | —                                              | none (default auto-config)                                |
| Admin    | `com.ulticode.admin`, `com.ulticode.modules.admin`, `com.ulticode.modules.event.inbox`, `com.ulticode.modules.backup`, `com.ulticode.modules.lease`, `com.ulticode.common` | 4 admin mapper packages                               | —                                              | none                                                    |
| App      | `com.ulticode.app`, `com.ulticode.audit`, `com.ulticode.common`, + 20+ `com.ulticode.modules.*` | via `MapperScanConfig` (appSqlSessionFactory)         | `MapperScanConfig`                             | none                                                    |
| Submission | `com.ulticode.submission`, `com.ulticode.modules.submission.*`, `com.ulticode.modules.queue`, `com.ulticode.websecurity` | 5 submission mapper packages                          | `DefaultSubmissionFencePort`, `DefaultSubmissionWritePort` | none                                                    |
| Notification | `com.ulticode.notification`, `com.ulticode.modules.notification.*`, `com.ulticode.modules.email` | 4 notification mapper packages                        | `DefaultNotificationAdminReadAdapter`          | none                                                    |
| Search   | `com.ulticode.search`, `com.ulticode.common`                                                  | — (none)                                              | —                                              | DataSource, DataSourceTxManager, MybatisPlus, MybatisAutoConfiguration |

### Shared package scan overlap (leakage points)

1. `com.ulticode.common` — scanned by **Auth, Admin, App, Search**
2. `com.ulticode.modules.event.inbox` — scanned by **Admin** (via
   `com.ulticode.modules.event.inbox`) and **App** (via
   `com.ulticode.modules.event.inbox`)
3. `com.ulticode.modules.submission.*` — Admin child scans
   `com.ulticode.modules.submission.controller/event/outbox/port`;
   Submission child scans
   `com.ulticode.modules.submission.mapper/outbox/result/created`
4. `com.ulticode.modules.notification.*` — App child scans
   `com.ulticode.modules.notification.event/intent/port`; Notification child
   scans `com.ulticode.modules.notification.channel/dispatcher/service/ledger/consumer/controller/adapter`
5. `com.ulticode.websecurity` — scanned by **Auth, App, Submission**

## 4. Per-child DataSource wiring

Each non-Search child receives `spring.datasource.*` properties via
`SpringApplicationBuilder.properties(...)` in
`CoreOwnerContextManager.start()` (lines 261-274). These properties are
consumed by `DataSourceAutoConfiguration`, which creates a
**child-local** `DataSource` that overrides any parent bean (Spring Boot auto-
config creates beans in the child context).

However, `CoreOwnerDataSourceConfiguration` already declares
`authDataSource`, `adminDataSource`, etc. in the **parent**. Since children
are standalone (no parent set), they never see parent DataSources — they
create their own from `spring.datasource.*`.

The parent DataSources/SqlSessionFactorys/TransactionManagers are effectively
**dead in the Core child path** — they exist but are never used by children
because `start()` does not pass `.parent(parentContext)`.

## 5. Root cause of enabled-owner wiring failure

All six Owner JARs are on the same Core classpath. Spring's
`@ComponentScan` with shared base packages (`com.ulticode.common`,
`com.ulticode.modules.event.inbox`, etc.) discovers sibling classes from
sibling JARs into each child context.

**No `@Primary` or bean-name override or scan-order tricks may be used** (per
constraints). Per-class exclusion is not viable as final architecture.

**Constraint:** Cannot add a second business implementation. Core parent
must delegate to existing Owner implementations.

### Known failure symptoms (from SERVICES_ISSUES.md:30-83, SVC-025)

- `DefaultAuditRecorder` missing `AuditSinkPort` — `com.ulticode.common.audit`
  discovered in child but `AuditSinkPort` not resolvable
- `SubmissionJudgedInboxBridge` missing Achievement consumer —
  `com.ulticode.modules.event.inbox` discovered in Admin child, but the
  Achievement consumer lives in App child
- `SubmissionUserReadPort` cross-jar ambiguity — same package in both App
  and Submission JARs, `SubmissionUserReadPort` interface in app-api resolved
  ambiguously when both children scan the package

## 6. Child context lifecycle

`CoreOwnerContextManager`:
- Constructor reads `core.owner-contexts.enabled` from env (defaults `true`)
- `start()` builds `SpringApplicationBuilder(module.bootConfiguration())`
  with `.web(NONE)` and `.properties(...)`
- **Does NOT call `.parent(parentContext)`** — confirmed at line 271-274
- Single-thread executor (`core-owner-bootstrap`), `startupTimeoutMs` (default
  120000)
- CAS-based handoff protocol: `TIMEOUT_CLAIMED` sentinel for timeout path

## 7. Readiness

`CoreReadinessService.snapshot()`:
- Checks `drainGate.isDraining()` — must be false
- Checks `ownerContexts.allReady()` — all enabled children must be READY
- Checks `judgeRequired` — probes `core.judge.readiness-url` (default
  `${CORE_JUDGE_READINESS_URL:}`)
  - If `judgeRequired=false` → judge `OPTIONAL` (always ready)
  - If URL blank → `NOT_CONFIGURED` (fail-closed)
  - If URL set → HTTP GET, 200 = `READY`, else `UNAVAILABLE`

`CoreReadinessController` exposes `GET /api/v1/core/health/ready` on port
9108 (`CORE_SERVER_PORT:9108`).

## 8. Smoke tests

`CoreApplicationSmokeTest`:
- 5 tests with `CORE_OWNER_CONTEXTS_ENABLED=false`, `CORE_JUDGE_REQUIRED=false`
- `startsWithExplicitOwnerRegistryAndCoreReadinessEndpoint` — asserts 6-module
  registry with correct names and transaction manager beans
- `appChildDoesNotUseCrossOwnerBroadModuleScan` — **static assertion only** on
  `@ComponentScan` annotation values; does not validate runtime bean isolation
- `bindsOneDatasourceAndTransactionManagerPerDataOwner` — asserts parent
  context has 5 distinct DataSources and transaction managers
- `disabledOwnerContextsFailReadinessClosed` — 503 when owners disabled
- `requiredJudgeWithoutReadinessEndpointFailsClosed` — NOT_CONFIGURED state

`CoreOwnerContextManagerLifecycleTest`:
- 6 deterministic tests for CAS handoff protocol (success, timeout, interrupt,
  created-but-unhanded, startup exception, stop-during-startup)

## 9. Evidence Level

Repository Implemented + Static Analysis. The enabled-owner child-context
boot is the one path that cannot be proven zero-infrastructure; it requires
MySQL + Redis + the actual Owner JAR classpath. Static assertions in
`CoreApplicationSmokeTest` cover annotation-level boundaries but cannot prove
runtime bean isolation without an enabled-owner startup.

## Verification

- `services/core` compiles and `CoreApplicationSmokeTest` passes with
  `CORE_OWNER_CONTEXTS_ENABLED=false` (zero-infra)
- Full enabled-owner boot requires `CORE_OWNER_CONTEXTS_ENABLED=true` +
  per-owner DB/Redis env → disposable validation only
- `git diff --check` must pass