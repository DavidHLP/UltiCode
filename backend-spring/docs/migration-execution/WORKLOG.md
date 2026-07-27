# Migration Worklog

Append-only log of significant events. NOT a task state source of truth
(see TASKS.yaml).

## 2026-07-25

### Initial scaffold

- Read MICROSERVICE_MIGRATION_GUIDE.md fully (947 lines, 0-947).
- Read backend-spring/AGENTS.md and backend-spring/pom.xml baseline.
- Inspected current repo state (backups drift, problem_notes drift, OAuth
  state binding gap, WS fail-open paths, users email non-unique).
- Created persistence files under `backend-spring/docs/migration-execution/`.

### P0-SCHEMA-001 — `backups` canonical migration

- Migration: `init-db/migrations/V20260724162738__Create_Backups_Table.sql`
- Status: done
- Commit: 3f1c61fd16f26a5686228e3f87ef7aac01bba462 (feat(db))

### P0-SCHEMA-002 — `problem_notes` schema convergence

- Migration: `init-db/migrations/V20260724162800__Converge_Problem_Notes_Schema.sql`
- Status: done
- Commit: 3f1c61fd16f26a5686228e3f87ef7aac01bba462 (feat(db), shared)

### TASKS.yaml rewrite (process correction)

- Line-level edits corrupted the file (duplicate status fields, missing
  P0-SCHEMA-003). Rewrote as pure top-level list, validated via
  `yaml.safe_load`. Installed `_tools/update_task.py`.
- Commit: 9172541ec9bfef35fb7db916608ab6340f2b9d57 (chore(migration))

### P0-SEC-001 — OAuth state cookie binding

- Status: done
- Implementation: `OAuthStateModule` constant-time compare via
  `MessageDigest.isEqual` before Redis consume; `OAuthStatePort` +
  `OAuthService` + `AuthController` signatures updated; 11+11 tests pass.
- Evidence: 1791 tests, BUILD SUCCESS 2026-07-25T00:39:16+08:00
- Commit: 90c6a0965838aec1e7b14fcad29870b902489080 (fix(security))

### Hash recording for P0-SCHEMA-001/002 + P0-SEC-001

- Commit: 65cc4af6b (chore(migration))

### Commit checkpoint (per advisory)

- After P0-SEC-001 done with three tasks uncommitted, stopped and
  recorded 3 atomic commits before continuing. RESUME/WORKLOG update.
- Commit: 4a60c4aa6 (chore(migration))

### P0-SEC-003 — WS validator unification + active/ban + fail-closed

- Status: done
- Implementation:
  - ErrorCode: WEBSOCKET_USER_BANNED (150006),
    WEBSOCKET_SESSION_MISSING (150007).
  - `DefaultWebSocketAuthenticator`: constructor adds Clock;
    `isBannedOrInactive(user)` rejects inactive / banned / future
    `banned_until` accounts.
  - `JwtChannelInterceptor.validateUserSession`: was log-and-return;
    now throws `WEBSOCKET_SESSION_MISSING` so SEND/SUBSCRIBE fail
    closed.
- Tests: 8 -> 12 in DefaultWebSocketAuthenticator; 9 -> 10 in
  JwtChannelInterceptor.
- Evidence: 1797 tests, BUILD SUCCESS 2026-07-25T00:47:35+08:00.
- Commit: 626e665a4755e0845072c2bd9d89f0953962dd86 (fix(security))
- Hash recorded: 62a2399 (chore(migration))

### WORKLOG/RESUME update after P0-SEC-003

- TokenBlacklistPort design note: port deliberately read-only; runtime
  revocation lives in RefreshTokenService. Phase 0 should NOT widen
  the read port.
- Commit: d7a04be5e (chore(migration))

### P0-SEC-004 — Effective permission expiry filter for /auth/permissions

- Status: done
- Implementation:
  - `PermissionServiceImpl.getUserPermissions`: LambdaQueryWrapper
    predicate `(expires_at IS NULL OR expires_at > NOW(clock))`. Null
    = permanent; future = valid; past = filtered.
  - The predicate lives at the SQL layer (not a Java post-filter), so
    the DB does the work and the service stays declarative.
- Tests:
  - `PermissionServiceTest`: 14 -> 15 (+1 filtersExpiredPermissions).
  - Removed a brittle wrapper-inspection test (relied on MyBatis-Plus
    lambda cache being initialized outside a running session).
- Documented semantics:
  - /auth/permissions is role-based via the JWT 'role' claim.
  - user_permissions layer is advisory: this filter is the data-honesty
    fix; full enforcement via GrantedAuthority / PermissionEvaluator
    is a Phase 2/3 per-endpoint opt-in.
- Evidence: 1798 tests, BUILD SUCCESS 2026-07-25T00:51:31+08:00.
- Commit: 0e9c3494773f235ba2f918f6993b7cb8f766b212 (fix(security))
- Hash recorded: dbdb04e (chore(migration))

### Status snapshot

- TASKS.yaml: 51 tasks, 5 done
  (P0-SCHEMA-001, P0-SCHEMA-002, P0-SEC-001, P0-SEC-003, P0-SEC-004)
- Local commits: 10 (atomic per task or task group + hash recording)
- Coverage: 100%
- Working tree: clean (modulo pre-existing untracked guide)
- PUSH: NOT pushed. Per GitHub Write Gate, push requires explicit user
  approval.

### Process rules (sticky going forward)

1. `in_progress` before any work on a task (via update_task.py)
2. Real validation command output captured before flipping to `done`
3. Evidence recorded via the script; never predict results
4. No `edit SWAP`/`DEL` on TASKS.yaml — ever. All status changes via script.
5. Commit + record hash in TASKS.yaml `commits:` field at the end of each
   task or tight task group. Don't accumulate > 1 task uncommitted.
6. No `git push` without explicit user approval (GitHub Write Gate).
7. Test files: prefer full `write` over `edit SWAP` when scope > 1 method.
8. When asserting on LambdaQueryWrapper at unit-test level, capture the
   wrapper and verify the SELECT was called; do NOT call getSqlSegment()
   outside a running MyBatis-Plus session (lambda cache NPE).

### Next actions

- P0-SCHEMA-003 — Inventory migration-only tables. Writes to DECISIONS.md
  (ADR-MIG-INV extension). No schema change.
- P0-SEC-002 — OAuth provider identity & verified-email binding
  (depends on P0-SEC-001, now unblocked).

- [2026-07-25T01:11:00+08:00] P0-SEC-002 done (commit f1be01b)
  • Created oauth_provider_identities table (V20260724165931)
  • Added OAuthUserInfo.emailVerified field
  • OAuthService refuses auto-link on unverified email
  • 1799 tests pass


- [2026-07-25T01:13:00+08:00] P0-JUDGE-001 done (commit d2e09a9)
  • ADR-MIG-JUDGE design written to DECISIONS.md
  • Generation fence, judge outbox, result outbox, dual-write window
  • Quick checks pass


- [2026-07-25T01:14:30+08:00] P0-ARCH-001 done (commit fac3d61)
  • Created TABLE_OWNERS.md (64 active tables + 12 migration-only)
  • Cross-referenced from COVERAGE.md
  • Quick checks pass


- [2026-07-25T01:24:00+08:00] P0-ARCH-002 done (commit 69cb328)
  • Added archunit-junit5 1.2.0 dependency
  • OwnerBoundaryArchTest with 4 frozen rules (admin→contest, moderation→users, submission→queue.service, submission→queue.outbox)
  • Baseline: 8+3+4+3 = 18 source files across 188 ArchUnit events
  • ADR-MIG-ARCH-BOUNDARY written to DECISIONS.md
  • Freeze store committed to git for drift detection
  • ./mvnw test -B passes (5 tests, 0 failures)


- [2026-07-25T01:25:30+08:00] P0-GATE done (commit 7773daa)
  • Phase 0 closed: all 10 tasks done
  • ./mvnw verify -B PASS (1804 tests, 0 failures)
  • JaCoCo check PASS
  • ArchUnit baseline green
  • Legacy judge feature-flagged
  • All migrations additive

### P1-INFRA-002 — backend-common extraction

- Status: done.
- Commit: 49dca6d (`refactor(common)`); hash recorded by c93aad9.
- Moved stable HTTP envelopes, trace/time primitives into `backend-common`.
- Added `RpcResult` with concrete JSON `ErrorPayload`, namespaced/base error
  types, and explicit trace/id metadata.
- Added ArchUnit dependency boundary rules and JSON/edge-case tests.
- `./mvnw -pl backend-common,backend-legacy -am test -B`: PASS;
  backend-common 62 tests, legacy suite green.
- Standards and Spec review PASS after all findings were fixed.

### P1-INFRA-004 — Gateway route/header baseline

- Status: done.
- Commit: 0901c38 (`feat(gateway)`); hash recorded by 81e920d.
- Added explicit Legacy-backed auth/admin/moderation/app/WS route families.
- Centralized identity-header removal and security headers.
- Full Docker smoke: 74 PASS, 0 FAIL, 0 SKIP.
- Standards/Spec/final review findings closed; final review 0 blockers.

### P1-API-001 — provider-owned contract modules

- Status: done.
- Commit: 3c2b0dc (`feat(rpc)`); hash recorded by a8cf667.
- Added `backend-auth-api` and `backend-app-api` with String-ID DTOs,
  idempotent commands, actor delegation and trace/deadline metadata.
- Added contract ArchUnit and shape tests; no implementation dependencies.
- Targeted reactor: 101 tests PASS.
- Standards/Spec/final review findings closed; final review 0 blockers.

### P1-INFRA-003 — Dubbo 3.3.6 Triple + Nacos registry (BLOCKED in sandbox)

- Status: blocked; P1-INFRA-003-DISC filed.
- Commits: c4d08a2 (parent pom dubbo-bom removed; backend-legacy
  pom wired with explicit dubbo-spring-boot-starter:3.3.6 +
  dubbo-registry-nacos:3.3.6), 86882f3 (placeholder
  HealthCheckService @DubboService + dubbo.application.protocol:tri +
  metadata-type:local + registry use-as-config-center/use-as-metadata-
  center as registry properties, not URL params, + 28848 host port),
  c8246b3 (TASKS.yaml: status blocked; P1-INFRA-003-DISC added;
  RESUME.md updated).

- Wiring is complete at compile + config-binding level:
  - dependency:tree shows spring-test 6.1.6 (NOT regressed to 5.3.39
    despite dubbo-bom 3.3.6 mega-BOM pinning Spring 5.3.x — we do
    NOT import dubbo-bom, only declare the two dependencies with
    explicit versions), dubbo-rpc-triple 3.3.6, nacos-client 2.5.1
    (transitive from dubbo-registry-nacos 3.3.6).
  - DubboBootstrapConfigTest (configuration-binding only) passes
    3/3: dubbo.application.name=ulticode-backend-legacy,
    dubbo.protocol.name=tri, dubbo.registry.address points to
    nacos://127.0.0.1:28848 with namespace=dev.
  - scripts/dev/dubbo-nacos-smoke.sh brings up MySQL + Redis + Nacos
    via docker compose, runs Flyway, installs backend-common into
    the local repo, starts backend-legacy in the same JVM, polls
    the Nacos instance list.

- Why this is blocked:
  - Spring Boot 3.2.5 + Dubbo 3.3.6 bootstrap completes in 4.5 s.
  - Dubbo logs: "Registered dubbo service
    ulticode/com.ulticode.dubbo.provider.HealthCheckService:1.0.0
    ... to registry service-discovery-registry://127.0.0.1:28848/...
    REGISTRY_CLUSTER=default:dev".
  - NacosNamingServiceWrapper initializes the nacos-client 2.5.1
    (auth plugin, ClientAuthServiceImpl, AbilityControlManager all
    observed).
  - The actual Nacos HTTP putInstance call is not observed in the
    Nacos container log within the 240 s smoke window; the
    /nacos/v1/ns/instance/list call returns an empty hosts array
    after 44 × 5 s probes.
  - P1-INFRA-003-DISC owns the action plan: rerun the smoke on a
    real Linux dev host, or escalate to Phase 4 P4-RPC-001 (real
    provider-owned contracts) which would re-exercise the same
    registration path under the real auth/registry contract.

- Lessons (sticky):
  - Phase 1 must ship at least one @DubboService. Dubbo does NOT
    register application instances on its own; it only registers
    exported service interfaces. An empty provider means no Nacos
    registry client is ever created and the smoke is a no-op.
  - dubbo.application.protocol defaults to "dubbo" in Dubbo 3.3;
    pin it to the actual protocol (here: tri) to avoid the
    ServiceInstanceHostPortCustomizer FAQ 4-2 fallback.
  - dubbo.application.metadata-type=local in Phase 1 avoids the
    METADATA_REGISTER round-trip that requires a config-center.
  - Phase 1 should NOT set `dubbo.config-center: { address: "" }`
    — that creates a placeholder ConfigCenterConfig and breaks
    `isEmpty(configCenters)` in
    DefaultApplicationDeployer.useRegistryAsConfigCenterIfNecessary.
    Leave config-center / metadata-report sections out entirely
    and pin `registry.use-as-config-center: false` /
    `use-as-metadata-center: false` as registry properties (not URL
    params).
  - scripts/security/bootstrap-nacos-user.sh requires
    MYSQL_ROOT_PASSWORD + NACOS_USERNAME + NACOS_PASSWORD exported
    in the calling shell. A `set +e` wrapper that "continues" on
    failure hides the missing-account root cause. Surface
    bootstrap errors and fail fast.
  - scripts/dev/dubbo-nacos-smoke.sh's `docker volume rm
    ulticode_mysql_data` wipes the Nacos schema too; wait for
    `nacos_config.users` to appear before running
    bootstrap-nacos-user.sh.

2026-07-26
P1-INFRA-005
- Added backend-auth / backend-admin / backend-app service shells.
- backend-common RpcHealthService placeholder; per-service @DubboService providers.
- Assigned distinct HTTP (9001/9002/9003) and triple (20881/20882/20883) ports.
- application.yml excludes DB/Flyway/Redis/Security so shells boot standalone.
- Added scripts/dev/start-service-shells.sh helper.
- Runtime verification: Nacos standalone + three jars; Nacos service list
  showed count=3 with [backend-auth, backend-admin, backend-app]; each had
  one healthy instance with correct Dubbo metadata.
- ./mvnw -pl backend-auth,backend-admin,backend-app -am -B verify PASS.
- P1-INFRA-005 marked done.

2026-07-27
P1-INFRA-003 (unblock + done)
- Fixed okhttp 5.3.2 placeholder Kotlin-Multiplatform jar (no classes) by
  downgrading backend-legacy pom to okhttp 4.12.0, matching Nacos 2.5.1 client
  expectation for okhttp3.Interceptor.
- Merged duplicate `management:` and `logging:` keys introduced by P1-OBS-001
  into single blocks in backend-legacy application.yml; YAML now loads cleanly.
- Added `TracerHolder` (ApplicationContextAware bridge) and made
  `DubboTraceFilter` SPI-friendly (no-arg constructor + lazy tracer lookup).
  Dubbo 3.3 ExtensionLoader can now instantiate the filter; unit tests still
  use the test constructor with a mock Tracer.
- Updated `scripts/dev/dubbo-nacos-smoke.sh` to obtain Nacos JWT accessToken via
  `/nacos/v1/auth/users/login` and query the instance list with that token
  (basic auth is rejected by Nacos 2.x).
- Runtime evidence: smoke PASS; Nacos instance list shows one healthy
  `ulticode-backend-legacy` instance in dev namespace / DEFAULT_GROUP.
- `./mvnw verify -B` reactor PASS after fixes.
- P1-INFRA-003 marked done; P1-INFRA-003-DISC marked superseded.

P1-GATE (closed)
- All Phase 1 tasks done or superseded.
- Coverage audit: 100% mapped, no empty acceptance criteria.
- `./mvnw verify -B` full reactor PASS.
- `dubbo-nacos-smoke.sh` PASS (Nacos registration with auth).
- P1-GATE marked done; moving to Phase 2.

2026-07-27 (continued)
P2-AUTH-001 setup
- Marked P2-AUTH-001 in_progress.
- Added backend-auth pom dependencies for Spring Security, validation, Redis, mail,
  MyBatis-Plus, MySQL connector, Redisson, JJWT.
- Wrote backend-auth application.yml with datasource, Redis/Redisson, Flyway, JWT,
  mail, MyBatis-Plus, management/tracing, and Dubbo/Nacos registry settings.
- Removed Phase 1 autoconfigure exclusions so backend-auth loads DataSource, Redis,
  Security, and Flyway.
- Verified backend-auth compiles with `./mvnw -pl backend-auth -am -B compile`.
- Next: copy auth entities/mappers from legacy, then AuthController/Service/Security.

2026-07-27 (continued)
P2-AUTH-001 setup (fix)
- backend-auth placeholder test failed after adding runtime security/datasource config:
  DataSource URL missing and default security blocked placeholder endpoint.
- Added H2 test-scope dependency to backend-auth pom.
- Wrote backend-auth src/test/resources/application.yml:
  - H2 in-memory datasource (MySQL compatibility mode)
  - Disabled Redis/Redisson/Security/ManagementWebSecurity autoconfig for tests
  - Disabled mail health indicator to avoid 503 on actuator/health
  - JWT test secret and Dubbo register/subscribe false
- Verified `./mvnw -pl backend-auth -am -B verify` PASS.
- Verified full `./mvnw verify -B` PASS (all modules).

2026-07-27 (continued)
P2-AUTH-001 planning
- Closed Phase 1 (P1-GATE done).
- Bootstrapped backend-auth runtime dependencies and application.yml.
- Fixed backend-auth placeholder tests with H2 test DB.
- Split P2-AUTH-001 into A-G subtasks; reordered so JWT/security (B) precedes
  refresh-token extraction (A).
- Delegated P2-AUTH-001-B (JWT/security plumbing) to worker subagent P2Auth001B.

2026-07-27 (continued)
P2-AUTH-001-B (picked up from vanished worker subagent)
- Copied com.ulticode.security.{jwt,csrf}.* to
  com.ulticode.auth.security.{jwt,csrf}.* in backend-auth so the
  auth service can independently sign/verify JWTs and run CSRF.
  backend-legacy keeps its own copies unchanged (Strangler Fig
  dual-run contract).
- Added hutool-all 5.8.44 to backend-auth/pom.xml for CSRF
  IdUtil.simpleUUID() generation.
- Added AuthSecurityConfig (narrower than legacy SecurityConfig:
  /auth/** + /actuator/health + /api/v1/auth/** permitAll,
  STATELESS, JWT filter chain, CSRF only when CsrfService bean
  is available) and AuthAuthenticationEntryPoint (returns
  Result.error with BaseErrorCode.UNAUTHORIZED).
- CsrfService rewritten with ObjectProvider<RedisTemplate> so
  the unit-test slice (which excludes RedisAutoConfiguration)
  can load the Spring context. requireRedis() throws
  IllegalStateException on null — prod-inert (Redis is always
  present at runtime), loud-fail at startup misconfig.
- CsrfValidationFilter and AuthAuthenticationEntryPoint
  reference BaseErrorCode (not ErrorCode) so backend-auth does
  not depend on backend-legacy. Byte values are kept identical
  per ErrorCodeDelegationTest's contract.
- 17 new unit tests added: JwtTokenProviderTest (10 tests
  across ValidateSecret/SignAndVerify/Expiration nested classes)
  and CsrfServiceTest (7 tests covering generate/rotate/clear/
  format/missing-Redis).
- ADR-MIG-AUTH-JWT-PLACEMENT written: records the two-step
  placement strategy (now: copy to backend-auth for task B
  acceptance; next: extract verify-only into backend-common in
  P2-AUTH-002 per guide §7.3/§11 App/Admin offline-verify
  requirement).
- RESUME.md refreshed: removed stale "Dirty Worktree: Yes"
  snapshot (TASKS.yaml/WORKLOG.md updates from the P2-AUTH-001
  split landed in e50c84b, before the most recent commit).
- Validation: ./mvnw verify -B full reactor PASS, 1795 tests,
  0 failures, 4 skipped.
- Commits:
  9b4aaf9 feat(auth): move JWT/CSRF plumbing into backend-auth
  ddf7faa chore(migration): mark P2-AUTH-001-B done; record hash

Next:
- P2-AUTH-001-A: refresh-token entity/mapper/service extraction
  into backend-auth (depends on B; A can now begin).
- P2-AUTH-001-E: RBAC/permission ownership (depends on B; can
  also begin in parallel with A — no shared files yet).

2026-07-27 (continued)
P2-AUTH-001-A (refresh-token ownership into backend-auth)
- Copied com.ulticode.modules.refreshtoken.{entity,mapper,service}.*
  to com.ulticode.auth.refreshtoken.{entity,mapper,service}.* so
  the auth service owns the refresh-token table, mapper, and
  rotation service. backend-legacy keeps its own copies
  unchanged (Strangler Fig dual-run).
- New backend-auth AuthErrorCode enum (9 AUTH 1xxxx constants,
  byte-identical to backend-legacy's ErrorCode AUTH sub-range)
  + AuthBusinessException that accepts NamespacedErrorCode so
  callers can throw AuthErrorCode (with HttpStatus) or
  BaseErrorCode (without HttpStatus) uniformly.
- Added AuthClockConfig providing Clock.systemDefaultZone bean.
  P1-INFRA-001 forbids backend-auth from depending on
  backend-legacy's com.ulticode.common.config.ClockConfig, so
  the bean had to be installed locally. The follow-up
  P2-DISC-002 owns the promotion to backend-common.
- 4 new unit tests added (RefreshTokenServiceTest): hash-only
  storage, atomic revoke-and-reissue, access-token rejection,
  replay-race detection. Mirror backend-legacy's existing
  RefreshTokenServiceTest contract.
- Discovery: P2-DISC-001 (BusinessException/ErrorCode promotion
  to backend-common) and P2-DISC-002 (Clock/TimeSource bean
  promotion). ADRs ADR-MIG-AUTH-EXCEPTION-PLACEMENT and
  ADR-MIG-CLOCK-PLACEMENT record the deferral decisions.
- Validation: ./mvnw verify -B full reactor PASS, 1795 tests,
  0 failures, 4 skipped.
- Commits:
  da6f598 feat(auth): move refresh-token ownership into backend-auth
  (P2-AUTH-001-A evidence will follow in the next commit)

Next:
- P2-AUTH-001-E: RBAC/permission ownership into backend-auth
  (depends on B; can begin in parallel with A — no shared files
  beyond the AuthErrorCode/AuthBusinessException plumbing now in
  place).
- P2-AUTH-001-C: AuthController + session/account adapters
  (depends on E and the gateway cutover plan).
