# P1-INFRA-006 Nacos Startup and Runtime Failure Contract

> status: CONTRACT WRITTEN; DISPOSABLE RUNTIME MATRIX: BLOCKED_EXTERNAL
> source baseline: `docs/architecture/evidence/P0-BASELINE-004-infra-graph.md`
> scope: repository startup/readiness semantics and disposable Nacos/Dubbo failure checks only

## 1. Boundary and non-claims

Nacos is a control-plane dependency for Dubbo naming, discovery, and Dubbo metadata/config plumbing. It is not a business-data store and it is not an owner of application tables. A Nacos failure therefore MUST NOT be reported as successful provider recovery, successful business work, or loss of owner data.

The HA overlay is an optional stateful reference profile. `docker-compose.ha.yml` supplies `nacos`, `nacos-2`, and `nacos-3` in cluster mode with a shared `nacos_config` database and an operator-supplied `NACOS_SERVERS` peer list. It explicitly does **not** claim transparent application failover or production failover (`docker-compose.ha.yml:1-17`; `docs/operations/deployment.md:43`).

`consumer.check: false` is only a Dubbo reference/provider-presence boot check. It does not prove that the Nacos client connected, that a provider is registered, or that a remote call can succeed. The contract below keeps those states separate.

### State vocabulary

| State | Meaning | What it does not prove |
|---|---|---|
| `PROCESS_UP` | JVM/container is alive and its process-level health surface answers | owner dependencies, Nacos, or provider registration |
| `OWNER_READY` | owner hard dependencies are healthy: DB + Redis for HTTP owners; Redis/worker marker for workers | Nacos registration or every remote provider |
| `REGISTRY_READY` | authenticated Nacos health/API probe answers | any provider is registered |
| `PROVIDER_REGISTERED` | the expected application instance is visible in the configured namespace/group and carries Dubbo metadata | the provider process is currently reachable or every RPC works |
| `REMOTE_CALL_READY` | a bounded RPC call reaches the expected provider and returns an explicit result | Nacos itself is currently healthy |
| `DEGRADED_RUN` | local work can continue, but registry/discovery-dependent work is unavailable or explicitly partial | successful discovery or fabricated fallback |
| `FAIL_START` | the supported startup path intentionally does not admit the workload because a required startup gate is unhealthy | a runtime outage after a workload was already admitted |
| `FAIL_CLOSED` | the affected remote/security/durable operation returns an explicit bounded error or remains pending; no success/empty result is manufactured | that unrelated local work must stop |

A check is a PASS only when its named state is observed. `PROCESS_UP`, `OWNER_READY`, or `REGISTRY_READY` alone MUST NOT be promoted to `PROVIDER_REGISTERED` or `REMOTE_CALL_READY`.

## 2. Current executable facts

### Startup and registry configuration

- Base Compose runs Nacos in standalone mode, exposes only internal `8848`/`9848`, waits for MySQL health, and probes `http://localhost:8848/nacos/` (`docker-compose.yml:55-89`). The development overlay binds the Nacos HTTP and gRPC ports to loopback only (`docker-compose.dev.yml:12-15`).
- The production profile requires `MODE: cluster`, a non-empty operator-supplied `NACOS_SERVERS`, a dedicated Nacos DB account, and Nacos auth token/identity values (`docker-compose.prod.yml:55-87`). Each Nacos-bound backend has a `depends_on: nacos: condition: service_healthy` startup gate; this is initial ordering, not ongoing failure supervision (`docker-compose.prod.yml:164-170`, `258-260`, `368-370`, `455-459`, `618-620`, `707-715`).
- The supported local launcher starts Compose infrastructure, waits for MySQL/Redis/Nacos health, then provisions Nacos users before the owner bootstrap and PM2 services (`scripts/dev/up.sh:390-405`, `498-529`). Thus a cold `up.sh` run with Nacos unavailable is `FAIL_START`/`START_BLOCKED` at the launcher gate, not a successful degraded deployment.
- Auth, Admin, App, Submission, Notification, and Judge all use `register-mode: instance`, a Nacos registry address, namespace/group parameters, and `consumer.check: false` (`services/auth/src/main/resources/application.yml:196-248`; `services/admin/src/main/resources/application.yml:111-161`; `services/app/app-web/src/main/resources/application.yml:178-227`; `services/submission/src/main/resources/application.yml:85-124`; `services/notification/src/main/resources/application.yml:102-143`; `services/judge/src/main/resources/application.yml:132-170`).
- Their global Dubbo consumer boundary is `timeout=3000`, `retries=0`; query references explicitly use the shared 800 ms / one-retry policy. The resilience filter counts no-provider and transport failures, opens after five consecutive failures for 30 seconds, and never manufactures a successful result (`services/docs/DEPENDENCY_RESILIENCE_RUNBOOK.md:7-23`; `services/platform/common/src/main/java/com/ulticode/common/rpc/RpcPolicy.java:63-106`; `services/platform/rpc-resilience/src/main/java/com/ulticode/rpc/resilience/DubboDependencyResilienceFilter.java:57-121`).
- Owner readiness endpoints check only their owner DB and Redis (`services/auth/src/main/java/com/ulticode/auth/adapter/in/web/AuthReadinessController.java:15-61`; equivalent Admin/App/Notification controllers). They do not check Nacos. Judge's worker marker checks Redis only (`services/judge-runtime/src/main/java/com/ulticode/modules/queue/processor/JudgeWorkerReadinessHeartbeat.java:19-75`); Search is not a Nacos/Dubbo workload and is governed by Redis + MeiliSearch (`services/search/src/main/resources/application.yml:7-63`).

### Security and smoke boundaries

- `scripts/security/bootstrap-nacos-user.sh` rejects unsafe/duplicate users, disables the built-in `nacos` account, scopes per-owner naming/metadata permissions, and writes the bootstrap transaction only after validating the Compose MySQL container (`scripts/security/bootstrap-nacos-user.sh:4-34`, `138-208`).
- `scripts/test/nacos-security-contract.sh` checks standalone-vs-cluster profile rules, per-owner registry credentials, namespace/resource scopes, disabled built-in account, and smoke safety guards. It is a static security contract; it is not a Nacos outage drill.
- `scripts/test/dubbo-nacos-smoke.sh` uses an owned mode-600 env file, a disposable Compose project, loopback Nacos, authenticated API calls, and Auth readiness before asserting instance registration and metadata. Two-replica mode asserts removal, restart, and single-instance failure (`scripts/test/dubbo-nacos-smoke.sh:33-47`, `294-365`, `513-569`, `668-710`).
- With `DUBBO_NACOS_SMOKE_REGISTRY_DRILL=1`, that existing smoke additionally stops Nacos while providers remain live, verifies provider readiness during the registry outage, boots one provider while Nacos is stopped, restarts Nacos, re-obtains an authenticated token, asserts provider re-registration, and (single-replica mode) asserts provider stop/restart recovery (`scripts/test/dubbo-nacos-smoke.sh:570-647`, `668-685`).

## 3. Failure and recovery contract

The matrix is intentionally written at observable boundaries. It does not infer registry state from an owner readiness response.

| Scenario | Required setup and assertion | Expected classification and result |
|---|---|---|
| Boot before Nacos | Keep MySQL/Redis available, keep Nacos stopped, and start one Nacos-bound owner/worker. Exercise both supported `up.sh`/Compose ordering and the direct JVM path only as a disposable diagnostic. | Supported launcher/Compose: `FAIL_START`/`START_BLOCKED` because Nacos health is a startup prerequisite. If a direct JVM survives because `check=false`, classify only as `DEGRADED_RUN` after `PROCESS_UP` + `OWNER_READY`; assert no `REGISTRY_READY` or `PROVIDER_REGISTERED`. If it exits, record `FAIL_START`; never call that a pass. Search worker is `N/A` for Nacos and follows its Redis/MeiliSearch marker. |
| Registry stop with existing providers | Start and register N provider(s), assert `PROVIDER_REGISTERED`, stop only Nacos, assert the provider process/owner readiness remains observable, and assert the authenticated Nacos API/list probe fails. | Existing provider processes remain independent of the Nacos process. Local owner/worker work may be `DEGRADED_RUN` while discovery is unavailable. Any new or required registry-dependent remote call is `FAIL_CLOSED` under the Dubbo timeout/retry/circuit policy. A cached direct RPC, if observed, is not evidence of registry recovery and MUST NOT satisfy this gate. |
| Provider restart while Nacos is healthy | With Nacos healthy and N provider(s) registered, stop one provider and wait for its instance removal; restart that provider; wait for its own readiness, instance count, and Dubbo metadata. | During the gap, that provider's dependent calls are `FAIL_CLOSED`; other registered replicas may serve only calls actually routed to them. Recovery is `PROVIDER_REGISTERED` + `REMOTE_CALL_READY`, not process-start alone. Existing two-replica smoke steps cover removal/restart/failure; the one-replica registry drill covers an explicit stop-to-zero-to-one restart path. |
| Reconnect after Nacos restart | Start registered provider(s), stop Nacos, then restart/boot one provider while Nacos remains stopped. Keep checking provider `OWNER_READY` separately. Restart Nacos and wait for authenticated API health, the expected instance count, and metadata. | Nacos API recovery is only `REGISTRY_READY`. The provider is recovered only after its instance is visible again (`PROVIDER_REGISTERED`). If an existing provider does not re-register, the result is `REGISTRY_READY + PROVIDER_UNRECOVERED`; remote calls remain `FAIL_CLOSED` and an operator/provider restart is required. The registry drill fails rather than fabricating reconnect success. |
| Full recovery gate | Require `REGISTRY_READY`, every expected `PROVIDER_REGISTERED` instance with metadata, each owner/worker's own readiness, and an explicit bounded remote-call result. | Only then may the affected RPC path resume. A Nacos health response without provider visibility, or provider visibility without a reachable endpoint, is not recovery. |

### Owner/worker classification

| Workload | Nacos role | Supported startup when Nacos is unavailable | Runtime Nacos loss while own dependencies are healthy | Required fail-closed boundary |
|---|---|---|---|---|
| Auth owner | Dubbo provider + consumer | `FAIL_START` at supported Compose/`up.sh` gate; direct boot is a diagnostic and may only be labeled `DEGRADED_RUN` after explicit readiness | Local Auth DB/Redis HTTP work may remain `OWNER_READY`; provider is not discoverable; callers cannot treat missing Auth as an affirmative identity/authorization answer | Auth identity/ban/authorization reads and management calls fail closed; only an explicit Auth response may authorize a caller (`services/app/app-web/src/main/java/com/ulticode/app/security/IdentityBanCheckAdapter.java:22-61`) |
| Admin owner | Dubbo consumer and admin-side adapters | Same `FAIL_START` gate; direct boot, if proven, is `DEGRADED_RUN` | Local DB/Redis-backed shell can remain ready; cross-owner pages/commands are explicit `PARTIAL` or `UNAVAILABLE`, never an empty-success result | Admin writes and required owner reads fail closed; optional profile enrichment may use its existing explicit `PARTIAL` response, while both required sources unavailable maps to `OWNER_QUERY_UNAVAILABLE`/503 (`services/admin/src/main/java/com/ulticode/modules/admin/projection/AdminUserEnricher.java:46-56`) |
| App owner | Dubbo provider + consumer | Same `FAIL_START` gate; direct boot, if proven, is `DEGRADED_RUN` | Own DB/Redis work may remain ready; Auth/App/Submission/Judge discovery-dependent paths are unavailable | Security, owner writes, submission dispatch, and required remote reads fail closed; no ban-check or command path may interpret unavailable as safe/success |
| Submission owner | Dubbo provider + consumer; internal HTTP/Actuator health | Same `FAIL_START` gate; direct boot, if proven, is `DEGRADED_RUN` | Owner persistence/queue state may be live, but remote provider calls and new registrations are unavailable | Commands/verdict/fence operations do not claim success; durable work remains pending or returns an explicit system/unavailable result under the owner contract |
| Notification owner | Dubbo provider + consumer | Same `FAIL_START` gate; direct boot, if proven, is `DEGRADED_RUN` | Local DB/Redis and already durable notification state may remain available; cross-owner recipient/read calls are unavailable | Required recipient resolution or notification command fails closed; no delivered/sent success is inferred from a registry outage |
| Judge worker | Dubbo provider + consumer; no business HTTP | Same `FAIL_START` gate in supported Compose; direct boot is `DEGRADED_RUN` only if its Redis/worker marker is healthy | Redis/PEL processing and worker marker are separate from Nacos; remote App/Submission calls can be unavailable | A failed remote verdict/result call is not ACKed as success; the judge path remains pending/PEL or records the explicit system failure according to `DefaultJudgeAttemptExecutor` and the resilience runbook |
| Search worker | No Nacos/Dubbo registry configuration | `N/A` for Nacos; governed by Redis + MeiliSearch health | Unchanged by Nacos loss | Search worker follows its own Redis/MeiliSearch heartbeat and PEL contract; do not include it in Nacos recovery counts |

## 4. Executable checks and stop conditions

### Repository/static checks

```bash
bash scripts/test/nacos-security-contract.sh
bash scripts/test/ha-profile-contract.sh
bash scripts/test/scale-topology-contract.sh
bash -n scripts/test/dubbo-nacos-smoke.sh
```

`ha-profile-contract.sh` proves the reference topology and, only when inputs are supplied, expands the HA Compose profile. Its `HA_RECONNECT_DRILL=1` is a Redis restart/reconnect check; it is not a Nacos failover proof. `scale-topology-contract.sh` invokes the two-replica smoke only when `DUBBO_NACOS_SMOKE_ENV_FILE` is supplied.

### Disposable Nacos/Dubbo check

Use an operator-owned mode-600 regular env file; do not put its contents in this document or command-line arguments:

```bash
ENV_FILE=/path/to/owned-disposable.env \
DUBBO_NACOS_SMOKE_REPLICAS=2 \
DUBBO_NACOS_SMOKE_REGISTRY_DRILL=1 \
bash scripts/test/dubbo-nacos-smoke.sh
```

The smoke's positive assertions are: authenticated Nacos API, Auth owner readiness, provider instance count, metadata, Nacos stop with live providers, provider boot during registry outage, Nacos restart/reconnect, provider removal, and provider restart. A non-zero result is a failed assertion; it MUST NOT be rewritten as a registry success.

### Current external gate

The full runtime matrix is **`BLOCKED_EXTERNAL`** in this checkout until a disposable Docker/Nacos input set is explicitly supplied. The shell environment has no `DUBBO_NACOS_SMOKE_ENV_FILE`, `HA_COMPOSE_ENV_FILE`, `REDIS_HA_CONFIG_DIR`, or `NACOS_SERVERS`. The HA path additionally needs operator-managed Nacos DB credentials/auth material and the peer list; the live Dubbo path needs its disposable DB/Redis/Nacos credentials, loopback ports, Docker daemon, and Java 17 toolchain. Their absence is an external-input block, not evidence of success or of a repository defect.

### Observed scoped checks

- `bash scripts/test/nacos-security-contract.sh` → `PASS`.
- `bash scripts/test/ha-profile-contract.sh` → topology checks pass; HA Compose expansion and Redis reconnect are `BLOCKED_EXTERNAL`; final status is `PASS_WITH_EXTERNAL_BLOCKERS`.
- `bash scripts/test/scale-topology-contract.sh` → static topology checks pass; merged production expansion and the two-instance Nacos smoke are `BLOCKED_EXTERNAL`.
- `bash -n scripts/test/dubbo-nacos-smoke.sh` → `PASS`.

No production Nacos registration, provider failover, HA promotion, traffic, SLO, or mixed-version claim is made here.

## Evidence level

Repository Implemented + Disposable Validatable. Static startup/security and provider registration paths are present. Registry stop/boot-before-registry/reconnect assertions are executable through the opt-in disposable smoke, but remain `BLOCKED_EXTERNAL` until the required disposable inputs and runtime are provided. HA remains a non-default reference profile and is not production failover evidence.
