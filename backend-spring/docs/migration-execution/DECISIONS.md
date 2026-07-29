# Migration Decisions

Architectural decisions (ADR) taken during migration execution.
Each entry must reference the guide section(s) it interprets.

## ADR-MIG-INV (Migration-only table inventory)

Context:
Guide §5.1 requires inventorying migration-only tables (e.g. `views`,
`virtual_contest_sessions`, `forum_community_*`, `system_announcement_*`,
`submission_statuses`, `contest_rankings`, `contest_analytics`,
`DailyRecommendation`, `email_templates` (bootstrap), `email_logs` (bootstrap))
before any DROP.

Decision:
Phase 0 produces an inventory document in DECISIONS.md listing each
candidate's purpose, current Producer/Consumer, and the future R/I/Q
classification. No DROP is performed in Phase 0.

Alternatives:
- DROP now and rely on backups — rejected (data loss risk; production not
  inspected).
- Keep tables and continue — accepted default until evidence supports either
  retire or formalize.

Consequences:
- Phase 7 has a documented basis for archival.
- Operators can flag required tables early.

Affected Tasks:
- P0-SCHEMA-003

### Inventory (Phase 0 sweep)

Source of truth: `init-db/migrations/V20260602_120000__Create_All_Tables.sql`
(repo-wide grep confirmed zero `@TableName(...)` references in any
current `src/main/java` entity for the tables below; they exist in the
schema but no Java code reads or writes them).

| Table | Migration line | Java ref | Disposition (Phase 7 candidate) |
|-------|----------------|----------|---------------------------------|
| `views` | L645 | None | Retire: `problem_views` exists |
| `virtual_contest_sessions` | L703 | None | Formalize (Virtual Contest) |
| `forum_community_profiles` | L769 | None | Retire (no read path) |
| `forum_community_settings` | L773 | None | Retire (no read path) |
| `system_announcement_notifications` | L433 | None | Retire (notifications table exists) |
| `system_announcement_read_records` | L440 | None | Retire (no read path) |
| `submission_statuses` | L797 | None | Formalize (SubmissionStatus enum) |
| `contest_rankings` | L810 | None | Formalize (Rating) |
| `contest_analytics` | L818 | None | Formalize (Analytics) |
| `DailyRecommendation` | L826 | None | Formalize (Recommendation) |
| `email_templates` | L951 | None | Bootstrap only |
| `email_logs` | L956 | None | Bootstrap only |

## ADR-MIG-WS-BINDING (Cookie-bound OAuth state, WS JWT validation)

Context:
Guide §7.1 §12 R4,R6 require fixing OAuth state token exposure (R4) and
WebSocket authentication hard-coding to query/subscription tokens (R6).
Inspection of `OAuthStatePort.validateState` shows it accepted only the
`state` query parameter, making CSRF replay trivial. `JwtChannelInterceptor`
unconditionally trusted `StompHeader.ACCESS_TOKEN` header on CONNECT,
ignoring HttpOnly cookie JWT.

Decision:
Handshake interceptor extracts from cookie only. STOMP CONNECT headers are
treated as a fallback only when the cookie path is unavailable in tests.
A single JWT validator (HMAC for compat window) is shared between HTTP
filter and WS authenticator.

Alternatives:
- Accept query token for ease of debugging — rejected (security risk).

Consequences:
- Blocks ad-hoc WS clients without cookie support.

Affected Tasks:
- P0-SEC-003, P2-AUTH-002

## ADR-MIG-WS-BLACKLIST (TokenBlacklistPort stays read-only in Phase 0)

Context:
Guide §7.1 mentions "access-token blacklist has reads but no complete
write chain in source." Inspection of `com.ulticode.modules.websocket.port.TokenBlacklistPort`
shows the port is deliberately read-only: a port-adapter audit removed the
unused `blacklistToken(...)` writers because runtime revocation is owned
by `RefreshTokenService` (DB-backed hash-only store, see
V20260606130000__Secure_Refresh_Tokens_And_Lock_Seed_Accounts.sql). The
WS port's Javadoc explicitly directs future admin instant-revoke work to
add a separate writer port rather than widen the read port.

Decision:
Phase 0 does NOT add write methods to `TokenBlacklistPort`. The current
write chain for token revocation is `RefreshTokenService.revoke*` (DB
hash-only + CAS). An admin-driven access-token kill switch, if added
later, gets its own `TokenRevocationWritePort` in the auth module.

Alternatives:
- Widen `TokenBlacklistPort` to add `blacklist(token, ttl)` — rejected
  (violates the port's documented read-only contract; surfaces writes
  on the WS hot path; duplicate state).
- Keep dual write: `RefreshTokenService` + Redis — rejected (two stores
  drift; guide §8.3 mandates single Owner).

Consequences:
- Phase 0 /auth/logout still relies on `RefreshTokenService` for
  revocation; WS sessions for the killed refresh chain naturally expire
  at access-token TTL.
- An admin instant-revoke feature requires a separate design pass and
  Phase 4 RPC hand-off to auth.

Affected Tasks:
- P0-SEC-003 (the read side is unchanged; this ADR documents the
  explicit non-decision on the write side).

## ADR-MIG-JUDGE (Judge outbox/fence/stream cutover design)

Context:
Guide §7.1 §8.3 require:
- Eliminating legacy Redisson `RQueue` dual-write.
- Completing `judge_outbox` + generation fence + `JudgeQueue` Redis Streams
  cutover.
- Adding a result outbox (post-verdict) to avoid JVM crash losing
  Contest/Notification/Achievement updates.
- Feature-flag driven dual-write window with exit criteria for legacy
  deprecation.

Current state (inspection of codebase):
- `JudgeQueue` exists (Redis Streams) but is not the primary write path.
- `judge_outbox` table exists but only covers "submit to judge"; verdict
  handling does NOT use outbox (violates §8.3).
- No generation/attempt fence around submission intake.
- Legacy `RQueue` is still active; no controlled cutover path.

Decision:
Phase 0 produces a design document (this ADR) covering the complete
judge outbox/fence/stream architecture; implementation spans Phase 0-2.
No code changes in Phase 0 beyond design.

Architecture (per guide §8.3):

1. **Generation fence (submission intake)**
   - `SubmissionWritePort.submit` writes `submissions` row with a
     `generation_id` (UUID) before any Redis enqueue.
   - Fence: CAS on `(submission_id, generation_id)` prevents duplicate
     judge worker processing.
   - Implementation: MyBatis-Plus `OptimisticLockerInterceptor` or manual
     version column.

2. **Judge outbox (submit → judge worker)**
   - `judge_outbox` table: `id`, `submission_id`, `generation_id`,
     `status`, `created_at`, `claimed_at`, `delivered_at`.
   - Written in the same DB transaction as `submissions` row.
   - Dispatcher (App-owned scheduled job):
     - Claims pending rows (`WHERE status='PENDING' AND claimed_at IS NULL
       LIMIT N FOR UPDATE SKIP LOCKED`).
     - Enqueues to `JudgeQueue` (Redis Streams) with `generation_id`.
     - Updates `judge_outbox.status='CLAIMED', claimed_at=NOW()`.
   - Reaper: retries stale `CLAIMED` rows after lease expiry.

3. **Result outbox (verdict → downstream)**
   - NEW `result_outbox` table: `id`, `submission_id`, `verdict`,
     `score`, `time_ms`, `event_type`, `payload`, `status`,
     `created_at`, `delivered_at`.
   - Judge worker writes result outbox in the same DB transaction that
     updates `submissions` with verdict.
   - Downstream consumers (Contest, Notification, Achievement) read from
     `result_outbox` instead of direct `submissions` reads.
   - Eliminates "verdict committed but JVM crashed before fan-out"
     data loss (guide §8.3).

4. **Feature-flag driven dual-write window**
   - Feature flag `judge.use_outbox=true` enables outbox path; false
     keeps legacy `RQueue` path.
   - Dual-write period: both `RQueue` and `JudgeQueue` receive messages;
     compare delivery rates.
   - Exit criteria (legacy `RQueue` deprecation):
     - 99.9% of submissions flow through outbox path for 7 days.
     - No dispatcher/reaper errors for 3 days.
     - Monitoring shows `RQueue` dequeue rate < 0.1%.
   - Once exit criteria met: remove `RQueue` write path, switch flag
     permanently.

Alternatives:
- Keep `RQueue` indefinitely — rejected (technical debt; no generation
  fence; duplicate state).
- Skip result outbox, rely on direct `submissions` reads — rejected (JVM
  crash loses verdict fan-out, violates §8.3).
- Use RabbitMQ instead of Redis Streams — rejected (adds new infrastructure;
  guide §8.3 prefers "不立即换 MQ").

Consequences:
- Phase 0 design only; implementation starts Phase 2 (P2-JUDGE-001).
- Adds two new tables (`result_outbox`; `judge_outbox` already exists).
- Requires dispatcher/reaper scheduled jobs (App-owned).
- Judge worker gains a new write path (`result_outbox`).

Affected Tasks:
- P0-JUDGE-001 (this design).
- P2-JUDGE-001 (implementation of generation fence + judge outbox).
- P2-JUDGE-002 (result outbox + downstream consumers).
- P7-JUDGE-001 (legacy `RQueue` removal).

## ADR-MIG-ARCH-BOUNDARY (ArchUnit baseline uses freeze-on-day-1)

Context:
Guide §5.1 §10 require cross-Owner Mapper/Entity separation
(Auth/Admin/App). P0-ARCH-002 acceptance criteria specify three rules:
 1. admin must not reach contest mapper/entity directly.
 2. moderation must not write users directly.
 3. submission must not reach queue internals outside the published port.

Inspection of `backend-spring/src/main/java/com/ulticode/modules/` shows
these rules are already violated today (cross-Owner reads/writes precede
the Owner manifest). Strict enforcement in Phase 0 would fail ~50+ tests
and block CI. A "skip rules until Phase 2" approach loses the change-detection
value of the baseline.

Decision:
Phase 0 implements the rules using ArchUnit `FreezingArchRule.freeze()`:
the current violation set is captured as a baseline. Subsequent runs
fail only when NEW violations are introduced (drift detection), not on
the existing frozen set. Phase 2/3 burn-down plans reduce each baseline
to zero per Owner.

Freeze store lives at `backend-spring/archunit_store/` (committed; reviewed
in PRs). Enabled via `backend-spring/src/test/resources/archunit.properties`
(`freeze.store.default.allowStoreCreation=true`,
`freeze.store.default.allowStoreReset=true`).

Rule → file → violation map (frozen baseline as of 2026-07-25):

| Rule | Source module | Forbidden target | Source files violating | ArchUnit events |
|------|---------------|------------------|------------------------|-----------------|
| admin_must_not_reach_contest_directly | admin | contest | 8 | 153 |
| moderation_must_not_reach_users_directly | moderation | user | 3 | 19 |
| submission_must_not_reach_queue_service | submission | queue.service | 4 | 10 |
| submission_must_not_reach_queue_outbox | submission | queue.outbox | 3 | 6 |

Source files in baseline (Phase 2/3 burn-down targets):

**Rule 1 — admin → contest (8 files):**
 - `AdminContestMutationService.java`
 - `AdminContestMutationServiceImpl.java` (heaviest: ~100 events)
 - `AdminContestProjection.java`
 - `AdminContestReadAdapter.java`
 - `AdminContestService.java`
 - `AdminContestServiceImpl.java`
 - `DefaultAdminAnalyticsPortAdapter.java`
 - `DefaultAdminContestProjection.java`

**Rule 2 — moderation → users (3 files):**
 - `DefaultModerationProjection.java` (read-port, deprecated in Phase 3)
 - `ModerationServiceImpl.java` (writes: userMapper.updateById)
 - `ModerationUserReadPort.java` (read-port, deprecated in Phase 3)

**Rule 3a — submission → queue.service (4 files):**
 - `DefaultRejudgePolicy.java`
 - `DefaultSubmissionWritePort.java`
 - `JudgingLeaseReaper.java`
 - `LegacyRejudgeStrategy.java`

**Rule 3b — submission → queue.outbox (3 files):**
 - `DefaultRejudgePolicy.java`
 - `DefaultSubmissionWritePort.java`
 - `JudgingLeaseReaper.java`

Alternatives:
- Strict enforcement today — rejected (fails ~50 tests; blocks CI).
- Skip rules entirely until Phase 2 — rejected (loses drift detection).
- Manual code review only — rejected (no automated enforcement).

Consequences:
- Baseline freeze file committed; PRs must not grow any rule's violation
  count without an explicit ADR + DECISIONS update.
- Phase 2/3 burn-down: per Owner, introduce RPC ports / event-driven
  projections to replace direct cross-Owner Mapper/Entity access.
- ArchUnit test fails on PR that introduces new cross-Owner leak.
- Test verifies 4 rules + 1 sanity test = 5 tests in
  `OwnerBoundaryArchTest`; ./mvnw test -B continues to pass.

Affected Tasks:
- P0-ARCH-002 (this ADR + the ArchUnit baseline test).
- P3-OWNER-001 (Phase 3 burn-down for admin/contest rule).
- P2-AUTH-001 (Phase 2 burn-down for moderation/users rule).
- P2-JUDGE-001 / P2-JUDGE-002 (Phase 2 burn-down for submission/queue rules).
## ADR-MIG-DUBBO-NACOS-PROBE

Context

P1-INFRA-003 is marked `blocked` (commit c8246b3) because the
sandbox's `./scripts/dev/dubbo-nacos-smoke.sh` never observed the
backend-legacy JVM actually putting an instance into Nacos within the
240 s smoke window. The wiring is fully complete at the compile +
configuration-binding level (spring-test 6.1.6 — NOT regressed to
5.3.39; dubbo-rpc-triple 3.3.6; nacos-client 2.5.1 transitive; the
Triple-protocol exporter reports the service-discovery-registry URL;
NacosNamingServiceWrapper initializes the auth plugin and
AbilityControlManager; backend-legacy completes Spring Boot startup
in 4.5 s), but the actual HTTP `putInstance` call is not observed in
the Nacos container log.

Decision

The unblock plan for P1-INFRA-003-DISC is **NOT** to add more
client-side knobs in backend-legacy. The wiring is already correct.
The missing signal is a sandbox-vs-Nacos external-storage problem.
Two independent verification paths were considered:

1. **Run a stock nacos-client 2.5.1 Java program** that bypasses
   Dubbo entirely and calls `NacosFactory.createNamingService(...)
   .registerInstance(...)` directly. If THAT call also fails to
   leave a mark on the Nacos side, the problem is the sandbox's
   Nacos 2.3.2 image + MySQL 9.1 external storage, not anything
   introduced by P1-INFRA-003. This probe is the cheapest possible
   decoupled validation. (The companion script
   `scripts/dev/nacos-smoke-isolated.sh` was authored and then
   removed in the same commit: the sandbox's `docker compose -f`
   invocation does not resolve the compose file in this restricted
   bash, so the probe cannot be re-run from here; the script is
   preserved in WORKLOG / this ADR for the next operator on a
   real host.)

2. **Escalate to Phase 4 P4-RPC-001** (provider-owned Contracts
   for Auth + App). Phase 4 re-exercises the exact same registration
   path under the real auth/registry contracts. If those do not
   register either, the failure is environmental, not in the
   wiring introduced in P1-INFRA-003. The placeholder
   `HealthCheckService` exists precisely so Phase 4 can replace it
   with the real provider contracts and the same `dubbo.scan.base-packages`
   + `@DubboService` + Triple + Nacos pipeline re-runs unchanged.

Alternatives

- Keep the `metadata-type: local` setting (preserves the existing
  `--spring.config.additional-location=file:...` interface; avoids
  the METADATA_REGISTER round-trip that needs a config-center).
- Add a long-running `dubbo.application.answer-foreign-domain=true`
  style flag. Rejected: no such flag exists in Dubbo 3.3 and the
  service-discovery protocol in 3.3 does not have a per-call
  extension point that would help here.
- Force `dubbo.registry.address=nacos://127.0.0.1:8848` (the
  in-container port) instead of the 28848 host port. Rejected: the
  host port is the only path the host JVM can reach the container;
  the in-container address is unreachable from the host.

Consequences

- The sandbox cannot prove the runtime acceptance criterion for
  P1-INFRA-003. The next agent or human operator must either (a) run
  the smoke on a real Linux dev host with `docker compose -f ... up`
  + a longer smoke window, or (b) escalate to Phase 4 P4-RPC-001.
- The `dubbo.scan.base-packages: com.ulticode.dubbo.provider` and
  the placeholder `HealthCheckService` stay in place until Phase 4
  replaces the placeholder with the real Auth / App provider
  contracts. They are a runtime-registration scaffolding, not a
  production contract; the user-facing surface of backend-legacy is
  not affected because the placeholder uses group `ulticode` and
  version `1.0.0` so it cannot collide with a Phase 4 export of the
  same group/version.
- A real `dubbo.bom` import is deliberately NOT used; the parent's
  dependencyManagement only carries explicit Dubbo 3.3.6 versions
  for the two backend-legacy dependencies. This is to avoid the
  Spring Framework 5.3 regression and `javax.servlet-api` 3.1.0
  pull-down that the dubbo-bom 3.3.6 mega-BOM would otherwise
  impose.

Affected Tasks

- P1-INFRA-003 (wiring; blocked on P1-INFRA-003-DISC).
- P1-INFRA-003-DISC (owner; this ADR + the action plan).
- P4-RPC-001 (fallback path; re-exercises the same registration
  pipeline under the real Auth / App provider contracts).

## ADR-MIG-AUTH-JWT-PLACEMENT

Context

P2-AUTH-001-B acceptance text says: "JwtProperties, JwtTokenProvider,
JwtAuthenticationFilter, CsrfService, CsrfValidationFilter copied/adapted
to backend-auth". The task title is "Move JWT/security plumbing into
backend-auth". At the same time, the guide §7.3 and §11 require
"App/Admin use resource-server style local JWT verification" and
"App/Admin offline-verify with public key", which means the **verify**
half of JWT must be reachable from App/Admin, not just from backend-auth.

Today JwtTokenProvider lives only in backend-legacy under
`com.ulticode.security.jwt`. App/Admin will live in separate Maven
modules and must not depend on backend-legacy or backend-auth. The
shared place for cross-service utilities is `backend-common` (P1-INFRA-002
extraction established the pattern: Result, PageResult, TraceIdUtil,
TimeSource, RpcResult).

Decision

Two-step placement:

1. **Phase 2 (P2-AUTH-001-B)**: copy `JwtProperties`, `JwtTokenProvider`,
   `CsrfService` (and the HTTP filter pair) into `backend-auth` under
   `com.ulticode.auth.security.{jwt,csrf}.*`. backend-legacy keeps its
   own copies untouched. This satisfies the P2-AUTH-001-B acceptance and
   keeps the Strangler Fig dual-run contract: Auth can independently
   sign tokens before the Gateway cutover while Legacy continues to
   verify them with the same HMAC secret.

2. **Phase 2 (P2-AUTH-002)**: extract a **verify-only** utility
   (`JwtVerifier` / `JwtTokenVerifier`) into `backend-common` so App/Admin
   can offline-verify tokens without pulling in the signer, the Redis
   CSRF service, or the SecurityFilterChain wiring. backend-legacy
   migrates to the shared verifier; backend-auth keeps the signer
   private. This addresses the §7.3 hot-path requirement and the
   §11 risk R3 ("verifier must not be able to sign").

Alternatives

- Put JwtTokenProvider directly in `backend-common` now (skip
  backend-auth copy): rejected because task B's acceptance
  explicitly says "copied/adapted to backend-auth", and the auth
  service must own its own signer before issuing tokens independently
  of Legacy. The verify-only extraction is cleaner when both sides
  exist.
- Keep JWT in `backend-legacy` and expose it via Dubbo: rejected
  because §11.2 / R3 explicitly forbids "verifier that can also
  sign" exposure beyond Auth; every request would incur an RPC.
- Move JWT to a new `backend-jwt` module: rejected — extra
  module for one class family is unjustified; backend-common
  already hosts cross-service utilities and P1-INFRA-002 is the
  established extraction path.

Consequences

- backend-auth gains a private JWT signing capability, sufficient
  to issue tokens for /auth/login, /auth/register, /auth/refresh.
- App/Admin continue to verify via their own (currently shared with
  backend-legacy) verifier; once P2-AUTH-002 lands, that verifier
  becomes a backend-common read-only utility.
- backend-legacy's copies stay until Phase 4 cutover, at which point
  both services drop the duplicated sources.
- A future task in Phase 7 (LEGACY-001) collapses the duplicate
  packages into the single backend-common verify utility.

Affected Tasks

- P2-AUTH-001-B (current; copy into backend-auth).
- P2-AUTH-002 (next; extract verify-only into backend-common).
- P7-LEGACY-001 (final; remove backend-legacy copies).
- P7-LEGACY-002 (drop duplicate JWT utility once verify is shared).

## ADR-MIG-AUTH-EXCEPTION-PLACEMENT

Context

P2-AUTH-001-A (refresh-token ownership into backend-auth) depends on
`com.ulticode.common.exception.BusinessException` and the AUTH
sub-range (`AUTH_*`, 1xxxx) of `com.ulticode.common.exception.ErrorCode`.
Both classes currently live only in backend-legacy, which violates the
new "backend-auth must not depend on backend-legacy" rule introduced by
P1-INFRA-001.

Two options were weighed:

- **Option C** (promote to backend-common now): lift `BusinessException`
  + the whole `ErrorCode` enum to `com.ulticode.common.exception` in
  backend-common. This eliminates the duplication entirely and matches
  the P1-INFRA-002 pattern (Result, PageResult, TraceIdUtil, etc.).
  Cost: P2-AUTH-001-A scope grows from 3 source files to ~250 lines of
  enum body + ~30 lines of exception + delegation test rewiring. It
  also forces backend-common to depend on `org.springframework.http.HttpStatus`
  for the `getHttpStatus()` accessor on `BusinessException`, which
  contradicts the NamespacedErrorCode javadoc that explicitly keeps
  HTTP mapping out of backend-common.

- **Option D** (local thin AuthBusinessException in backend-auth):
  create a backend-auth-private `AuthBusinessException` + a backend-auth
  `AuthErrorCode` enum restricted to the AUTH 1xxxx range (9 constants
  today, byte-identical numeric values to backend-legacy's enum).
  Cost: temporary duplication of `BusinessException` semantics across
  backend-auth and backend-legacy; convergence will need a follow-up.

Decision

**Option D**, because:

1. P2-AUTH-001-A's acceptance_criteria explicitly scope the work to
   the three refresh-token source files; promoting
   `BusinessException`/`ErrorCode` would either expand that scope
   (silently violating §11's "no silent architecture changes") or
   require splitting the task into two sub-commits — but those two
   sub-commits have no incremental value at the current moment (the
   legacy enum is still in use by every module).
2. The current `AuthErrorCode` is intentionally a 9-constant subset,
   not a copy of the full 200-line enum. This makes the eventual
   Option C promotion strictly an additive merge: backend-legacy's
   ErrorCode AUTH 1xxxx values stay byte-identical and the
   `BusinessException` shape is preserved. The migration cost is
   deferred to a single follow-up task instead of being smeared
   across P2-AUTH-001-A's commit history.
3. NamespacedErrorCode explicitly says HTTP-status mapping stays in
   each module's enum; making `AuthErrorCode` own its HTTP mapping
   aligns with the documented contract.

Affected Tasks

- P2-AUTH-001-A (current): builds on `AuthErrorCode` +
  `AuthBusinessException` instead of legacy's.
- P2-DISC-001 (new, owned by P2-AUTH-001-A): retro-fit
  backend-legacy's `ErrorCode` to delegate to
  `com.ulticode.common.error.BaseErrorCode` + the new backend-auth
  `AuthErrorCode` for the AUTH 1xxxx range; consider promoting the
  non-AUTH codes to module-local enums (UserErrorCode,
  ProblemErrorCode, etc.) when each respective service extraction
  lands. Eventually, `BusinessException` itself can be promoted
  once the HTTP-status coupling is removed (or generalised to
  accept a generic `HttpStatusAware` interface).
- P7-LEGACY-001 (final): delete backend-legacy's
  `com.ulticode.common.exception.ErrorCode` once every consumer has
  moved to a module-local enum.

## ADR-MIG-CLOCK-PLACEMENT

Context

P2-AUTH-001-A copied `RefreshTokenService` into backend-auth. The
service has `private final Clock clock;` (injected by Lombok
`@RequiredArgsConstructor`). backend-legacy provides the `Clock` bean
via `com.ulticode.common.config.ClockConfig`. Because backend-auth must
not depend on backend-legacy (P1-INFRA-001), the bean is invisible and
the unit-test slice fails to start the Spring context.

`TimeSource` / `TimeSourceHolder` were already promoted to backend-common
by P1-INFRA-002, but the *bean wiring* stayed in backend-legacy
(`com.ulticode.common.time.TimeConfig` + `SystemTimeSource`). So the
*type* is shared but the *instance* is not.

Decision

P2-AUTH-001-A carries a private `com.ulticode.auth.config.AuthClockConfig`
(Clock.systemDefaultZone()) so the auth service can start. The bean
shape matches the legacy config byte-for-byte; tests use `@MockBean`
or `@Primary` to inject a fixed clock as before.

A follow-up task, P2-DISC-002, will promote both the `Clock` and
`TimeSource` bean configuration into backend-common. After that:

- `backend-common` ships `@Configuration` that declares both beans.
- `backend-legacy` deletes `com.ulticode.common.config.ClockConfig` and
  `com.ulticode.common.time.TimeConfig` (P7-LEGACY-001 in the final
  clean-up).
- Extracted services stop adding per-module `AuthClockConfig` /
  `AdminClockConfig` / `AppClockConfig` copies; they just depend on
  backend-common.

Alternatives

- Promote only the `Clock` bean (skip TimeSource) — rejected because
  the two share the same configuration concern and promoting one
  without the other leaves a confusing split.
- Keep `ClockConfig` in backend-legacy forever — rejected because
  P1-INFRA-001 already declared cross-service visibility of any
  backend-legacy bean is forbidden.
- Make every service ship its own `*ClockConfig` — explicitly rejected
  as a maintenance footgun; the discovery that prompted this ADR is
  the second time the same kind of copy has been needed
  (P1-INFRA-005 also hit a similar wiring problem with service shells).

Affected Tasks

- P2-AUTH-001-A (current): carries `AuthClockConfig` as a local
  workaround.
- P2-DISC-002 (new, owned by P2-AUTH-001-A): promote bean config
  to backend-common.
- P2-AUTH-001-G (later): drop `AuthClockConfig` once P2-DISC-002 lands.
- P7-LEGACY-001 (final): delete `ClockConfig` and `TimeConfig` from
  backend-legacy.

## ADR-MIG-CROSS-CUTTING-PORTS-PROMOTION

Context

P2-AUTH-001-E (RBAC/permission ownership into backend-auth) requires
backend-auth-local copies of two cross-cutting ports that currently
live in backend-legacy:

- `com.ulticode.common.uuid.UuidGenerator` (port) +
  `com.ulticode.common.uuid.ProdUuidGenerator` (`@Component` adapter)
- `com.ulticode.common.auth.CurrentUserProvider` (port) +
  `com.ulticode.common.auth.SecurityCurrentUserProvider`
  (`@Component` adapter)

`grep` over backend-legacy finds **112 files** that import one or both
of these ports. Promoting the *ports* (interfaces) to backend-common
without breaking the existing backend-legacy consumers would require
either:

1. **In-place rename** of backend-legacy's
   `com.ulticode.common.uuid.UuidGenerator` to
   `com.ulticode.common.uuid.UuidGenerator` in a new home
   (`com.ulticode.common.uuid.UuidGenerator` in backend-common), then
   rewriting the 112 import statements. This is a
   cross-cutting-ripple change touching every service module and every
   test. It contradicts the Phase 2 Strangler Fig contract (no silent
   changes to backend-legacy) and the §15 "additive only" rule for
   schema-equivalent code paths.

2. **Bridge**: backend-legacy keeps its own copy; backend-common
   installs the canonical version; backend-legacy's copy becomes a
   `extends` or a deprecated facade that delegates. This preserves
   source compatibility but duplicates the type system across two
   modules and creates a future migration tax when the legacy copies
   are removed.

Decision

**Continue Option D** (per-call local copies in backend-auth) and
**defer** the promotion of `UuidGenerator` and `CurrentUserProvider`
ports until Phase 3 or Phase 7.

Rationale:

- P2-AUTH-001-E's acceptance_criteria explicitly scope the work to
  the permission domain (PermissionService, PermissionVocabulary,
  UserPermission, RolePermission, mappers). Promoting cross-cutting
  ports is not in scope and not blocked by P2-AUTH-001-E (the ports
  can be copied locally without service interruption).
- The cost of in-place promotion (rewriting 112 import statements,
  re-verifying the entire backend-legacy test matrix, and risking a
  boot-time circular wiring surprise) is too high for a
  Strangler-Fig Phase 2 task whose goal is *extraction*, not
  *consolidation*.
- P2-DISC-001 (BusinessException / ErrorCode), P2-DISC-002 (Clock /
  TimeSource beans), and the new P2-DISC-003 (UuidGenerator /
  CurrentUserProvider ports) together describe a single follow-up
  effort: a **Phase 3 / Phase 7 cross-cutting promotion batch** that
  moves all four families of cross-cutting types from backend-legacy
  to backend-common in a coordinated commit series. The four
  discoveries are intentionally *not* promoted piecemeal because each
  one is a 5-50 file import-rewrite and the combined batch will be
  more reviewable as a single PR.

Alternatives

- Single promotion batch **now** (per the advisory that prompted
  this ADR): rejected because the 112-file import-rewrite is a
  cross-cutting break that requires its own Phase, its own gate, and
  its own rollback plan. Phase 2's job is to *extract* without
  *consolidating*.
- Per-port promotion as each task needs it: rejected because four
  separate promotions will quadruple the test-matrix hits without
  saving any time.
- Promote adapters (ProdUuidGenerator, SecurityCurrentUserProvider)
  too: rejected because adapters depend on the security/UUID
  libraries a given module wires in, so adapters stay module-local;
  only the *port interfaces* belong to backend-common.

Affected Tasks

- P2-AUTH-001-E (current): carries
  `com.ulticode.auth.uuid.{UuidGenerator, ProdUuidGenerator}` and
  `com.ulticode.auth.authport.{CurrentUserProvider,
  SecurityCurrentUserProvider}` as local copies.
- P2-DISC-003 (new, owned by P2-AUTH-001-E): add to the
  cross-cutting promotion batch alongside P2-DISC-001 and
  P2-DISC-002; the batch is owned by a single Phase 3 follow-up
  task that covers all four port families in one reviewed PR.
- P7-LEGACY-001 (final): delete backend-legacy's
  `com.ulticode.common.uuid.*` and
  `com.ulticode.common.auth.{CurrentUserProvider,
  SecurityCurrentUserProvider}` once every consumer has moved to
  the backend-common ports.

### ADR-P3-BURNDOWN-001: TestCaseOwnerPort as a sibling to ProblemOwnerPort

**Date:** 2026-07-28  **Status:** Accepted

**Context.** The p3_owner_001_f ArchUnit rule froze 8 admin foreign-mapper write violations: 5 on `TestCaseMapper` (AdminTestCaseService) and 3 on `ProblemMapper` (ProblemImportServiceImpl). The burn-down had to route every write through an owner port in `com.ulticode.modules.problem.port`. The original task AC1 named `ProblemOwnerPort` for the TestCaseMapper calls, but `ProblemOwnerPort`'s javadoc scopes it to the `problems` row, and `test_cases` is a separate table.

**Decision.** Create a sibling `TestCaseOwnerPort` (+ `DefaultTestCaseOwnerPort`) for `test_cases` writes, and extend `ProblemOwnerPort` only for the problem-row import writes (`insertImportedProblem`, `applyImportedUpdate`). Rationale: `test_cases` is a distinct table owned by the problem domain; a dedicated port preserves one-port-per-table cohesion (mirroring the Contest/Submission/Forum/Solution owner ports) rather than overloading `ProblemOwnerPort` with a second table's primitive commands. Import row defaults and PartialUpdate null/blank-skip semantics move into `DefaultProblemOwnerPort` so the owner owns row construction.

**Consequences.** + Clear single-table ownership; the future RPC wire shape (P4-RPC-001) maps one port to one table. + Admin's `TestCaseMapper` becomes read-only. - Two ports instead of one for the problem module; acceptable given the per-table cohesion convention already established across the Phase 3 owner ports. AC1 wording in TASKS.yaml revised to reflect the sibling port.

### ADR-P3-AUDIT-001-followup: AuditOutboxMapper scan coverage fix

**Date:** 2026-07-28  **Status:** Accepted

**Context.** `AuditOutboxMapper` (P3-AUDIT-001) was placed in `com.ulticode.modules.admin.outbox`. The application's `@MapperScan("com.ulticode.modules.**.mapper")` does not cover that package, and once an explicit `@MapperScan` is present, MyBatis-Spring does not auto-register bare `@Mapper`-annotated interfaces outside the scan path. The result: the mapper bean was never created, so `DefaultAuditSinkAdapter` -> `AuditAspect` wiring threw `NoSuchBeanDefinitionException` at full Spring-context startup — a production-startup regression. The unit suite (all mocks) did not catch it; the full-context `*IT` tests were the canary.

**Decision.** Move `AuditOutboxMapper` to `com.ulticode.modules.admin.outbox.mapper`, matching the documented convention in `MapperConfig` (mappers live in `**.mapper` sub-packages; cf. `queue.outbox.mapper`, `notification.ledger.mapper`). The record/processor/dispatcher stay in `admin.outbox` (they are not mappers).

**Consequences.** + Production startup works; the IT canary passes. + No special-casing of `@MapperScan`. - The mapper lives one package deeper than its record; acceptable and consistent with existing outbox/ledger precedents.

### ADR-P4-RPC-001: Contract design decisions for Contest/Submission RPC providers

**Date:** 2026-07-28  **Status:** Accepted

**Context.** P4-RPC-001 required completing the backend-app-api Dubbo provider surface — ContestAdministrationService and SubmissionAdministrationService were missing. During implementation, four non-trivial design decisions surfaced that affect how the P4-CUTOVER tasks will wire the Provider implementations.

**Decision 1 — ContestAdminViewDTO has no version field.** The Contest entity has no `@Version` column; it is state-machine driven (`status` ∈ DRAFT, UPCOMING, RUNNING, FINISHED, CANCELLED). Contest lifecycle transitions are protected by state validation (e.g. must be UPCOMING to start), not optimistic locking. Therefore the read-back DTO carries only `contestId / title / status`. Unlike ProblemAdminViewDTO, which includes `version` because the Problem entity has a `@Version` column.

**Decision 2 — `expectedVersion` on Contest transition commands is an opaque state-machine fence token, not an optimistic-lock version.** The field name is retained for signature stability (a future provider might introduce a generation counter), but the javadoc explicitly disclaims the optimistic-lock interpretation. The provider interprets it as a status-level fence: rejecting the command when the contest's current status does not match. For UpdateContestCommand the fence may be relaxed in DRAFT (any edit is legal).

**Decision 3 — RejudgeCommand carries no caller-supplied generation.** The existing admin HTTP `RejudgeRequest` carries only `submissionId + notifyUser`. The server-side `RejudgePolicy.rejudgeFenced` reads the submission's `generation` from the DB row inside an atomic `bumpGeneration` CAS — the caller cannot know the current generation without an extra read, and the CAS handles the race. Mirroring this shape keeps the RPC command aligned with the HTTP contract. A future caller-supplied optimistic-concurrency field would be a §6.4 additive change.

**Decision 4 — CreateContestCommand create-minimum is driven by DB schema constraints, not by mirroring CreateProblemCommand.** The `contests` table has `contest_type`, `start_time`, and `duration_minutes` as `NOT NULL` with no DB default; `scoring_mode` is `NOT NULL DEFAULT 'SCORE'` (nullable on the command, provider applies the default). Unlike Problem, where the entity has safe defaults for status/version/flags, Contest cannot be created with `null` in these columns. Per §6.4 fields only grow additively, so locking the create-minimum now avoids a second command later.

**Decision 5 — Contest announcements deferred to App HTTP API only.** The plan's original "Decision 1" said announcements defer to "ContentModerationService or App HTTP API." ContentModerationService is moderation-only (HIDE/RESTORE/DELETE/UNDELETE on existing content), not content creation. The corrected deferral target is App HTTP API only.

**Alternatives considered.** (1) Add `@Version` to Contest entity to enable true optimistic locking — rejected as a data-model change beyond P4-RPC-001's contract-only scope. (2) Expose contest announcements on a separate RPC provider — rejected; no real Consumer justifies the cost yet (§4.2 "初期不强行创建无 Consumer 的 Provider").

**Consequences.** Provider implementations in P4-CUTOVER-001/002 must: (a) apply state-machine validation, not version-column locking, for Contest transitions; (b) delegate rejudge to `RejudgePolicy` without expecting a caller generation; (c) apply DB defaults for `scoring_mode` when the CreateContestCommand field is null.

**Affected Tasks:** P4-RPC-001 (done), P4-CUTOVER-001, P4-CUTOVER-002.


### ADR-P4-CUTOVER-002-AUDIT: Scope split — forum/solution deferred to P4-CUTOVER-004

**Date:** 2026-07-29  **Status:** Accepted

**Context.** P4-CUTOVER-002 AC1 originally read "Admin contest/submission/forum/solution
management routed through Dubbo." The task title is "Contest/Submission family cutover."
During completion audit, AdminForumController and AdminSolutionController were confirmed
to exist but are NOT routed through Dubbo. Forum/Solution moderation writes go through
ContentModerationService — a cross-cutting HIDE/RESTORE/DELETE/UNDELETE contract that has
no Provider implementation yet (deferred from P4-CUTOVER-001 moderation paths).

The migration guide (line 662) recommends ProblemList/Solution/Forum as a separate
cutover step, ordered before Contest/Submission.

**Decision.** (1) Correct AC1 to "Admin contest/submission management routed through
Dubbo" to match the task title. P4-CUTOVER-002 stays done for its Contest/Submission
scope — the delivery is complete and verified (1893/0). (2) Create P4-CUTOVER-004
(Forum/Solution/ContentModeration cutover, pending) to track the deferred forum/solution
work. P4-CUTOVER-004 depends on implementing ContentModerationProvider first.

**Alternatives considered.** (1) Keep P4-CUTOVER-002 in_progress until forum/solution
are done — rejected because forum/solution has a hard dependency (ContentModerationProvider)
that cannot be resolved within this task, and the Contest/Submission delivery is independently
complete. (2) Leave AC1 unchanged and mark task done anyway — rejected as dishonest
closure per audit principle.

**Consequences.** P4-GATE now depends on P4-CUTOVER-004 in addition to P4-CUTOVER-002/003.
P4-CUTOVER-004 will require: ContentModerationProvider impl, AdminForumController +
AdminSolutionController moderation route wiring, feature flag.

**Affected Tasks:** P4-CUTOVER-002 (done), P4-CUTOVER-004 (pending), P4-GATE (pending).


### ADR-P4-CUTOVER-003-AUDIT: AC correction — Judge/WS/Notification runtime scope

**Date:** 2026-07-29  **Status:** Accepted

**Context.** P4-CUTOVER-003 AC1 originally read "Judge worker / WS / Notification
runtime on App process" and AC2 read "Multi-instance WS bridge uses sticky session
or Redis broadcast." Both describe post-deployment runtime states that require P4-GATE
(three services independently deployed). In the current monolith, these states are
not achievable — the work is to wire feature-flagged deployable boundaries so the
runtime components can later move to the App process without code rewrites.

Three independent facts drive the scope split:
1. Judge queue cutover infrastructure (M3a/M3b/M3c flags) already exists independently
   of Dubbo cutover — activation is an ops concern, not a contract task.
2. WS multi-instance broadcast requires Redis Pub/Sub infrastructure; guide line 145
   explicitly defers: "App 初期单实例或粘性会话；多实例前需外部广播/relay."
3. Notification admin writes (create/delete/update) and batch rejudge are genuine
   Dubbo cutover candidates following the CUTOVER-001/002 pattern.

**Decision.** (1) Correct AC to "Admin notification management + batch rejudge routed
through Dubbo; flag-controlled dual-path; zero behavior change at flag=off."
P4-CUTOVER-003 stays done for this scope. (2) Create P4-CUTOVER-005 (WS multi-instance
broadcast bridge, pending) for the deferred WS work. (3) Judge queue activation stays
on existing independent feature flags.

**Consequences.** P4-GATE now depends on P4-CUTOVER-005 in addition to 003/004.
P4-CUTOVER-005 will require Redis Pub/Sub relay infrastructure for WS push adapters.

**Affected Tasks:** P4-CUTOVER-003 (done), P4-CUTOVER-005 (pending), P4-GATE (pending).


### ADR-P4-CUTOVER-004: ContentModeration partial-action scope

**Date:** 2026-07-29  **Status:** Accepted

**Context.** ContentModerationService.apply supports 4 actions (HIDE/RESTORE/DELETE/UNDELETE)
across 2 content types (forum_post, solution) = 8 cells. AdminForumService and AdminSolutionService
currently only implement soft-delete (DELETE). HIDE/RESTORE/UNDELETE methods do not exist.

**Decision.** Implement DELETE only (2/8 cells). Unsupported actions return CONTENT_STATE_CONFLICT
(40902) with an action-specific error message so clients can distinguish unimplemented from
real state conflict. This follows the honest-scope principle: the Provider compiles, tests
pass, and the unsupported paths fail loudly rather than silently.

**Consequences.** HIDE/RESTORE/UNDELETE need follow-up work: add the methods to
AdminForumService/AdminSolutionService, then extend Provider dispatch. The contract
(ApplyModerationCommand) already defines all 4 actions — no contract change needed.

**Affected Tasks:** P4-CUTOVER-004 (done).

### ADR-P2-DISC-004: MySQL 9.1 portable DDL and migration rollback policy

**Date:** 2026-07-29  **Status:** Accepted

**Context.** MySQL 9.1 rejects `ALTER TABLE ... DROP COLUMN IF EXISTS` and `ALTER TABLE ... DROP INDEX IF EXISTS` with syntax error 1064 (these clauses are MariaDB/PostgreSQL extensions). In `V20260727021915__Add_Authz_Version_And_Refresh_Token_Family.sql`, defensive commented-out rollback lines used `DROP COLUMN IF EXISTS` syntax.

Per project database migration invariants (AGENTS.md + guide §15), applied Flyway migration files are immutable, and application rollback strategy relies on additive schema preservation rather than manual SQL down-migrations.

**Decision.**
1. Adopt Option A: Migration comments and documentation must use plain MySQL 9.1 DDL syntax (`ALTER TABLE ... DROP COLUMN ...`) without non-standard `IF EXISTS` clauses on `ALTER TABLE`. Option A applies to future migrations only; V20260727021915 is left untouched per AGENTS.md migration-immutability.
2. Rollback strategy remains application-rollback + additive-schema preservation (guide §15).
3. The `disposable-verify/` scaffold (`disposable-verify/docker-compose.verify.yml`) is retained as a documented regression test fixture for additive migrations going forward per AC2 option (b).

**Consequences.** All future Flyway migrations must target standard MySQL 9.1 syntax.

**Affected Tasks:** P2-DISC-004 (done).


### ADR-P4-GATE-AUDIT: Code-level Phase 4 Gate verification vs. ops-level live deployment validation

**Date:** 2026-07-29  **Status:** Accepted

**Context.** Phase 4 Gate (`P4-GATE`) acceptance criteria and validation list both code-level contracts (three independent modules, single-hop RPC, timeout/retry policies, no shared Mapper jars, contract tests) and ops-level live deployment conditions (three independently running Compose/K8s services, live fault-injection matrix, live N-1 runtime compatibility checks).

In the current development environment (monolith transitional execution), all 7 Phase 4 cutover tasks (`P4-RPC-001`, `P4-RPC-002`, `P4-CUTOVER-001`, `P4-CUTOVER-002`, `P4-CUTOVER-003`, `P4-CUTOVER-004`, `P4-CUTOVER-005`) are fully implemented, feature-flagged, and verified via unit, integration, contract, and ArchUnit test suites.

**Decision.**
1. **Code-level Gate Verification (Closed in Phase 4):**
   - Three service modules (`backend-auth`, `backend-admin`, `backend-app`) are modularized with independent application entry points and Dubbo Provider implementations.
   - Single-hop RPC invariant enforced via `SingleHopArchTest` / `OwnerBoundaryArchTest`.
   - Centralized timeout (3000ms) and retry=0 idempotency policy enforced via `RpcPolicy`.
   - Zero shared Mapper jars across contract modules (`backend-auth-api`, `backend-app-api`).
   - Contract shape compatibility covered by `ContractShapeTest`.
   - Feature-flagged dual-path routing implemented for all cutover domain families.
2. **Ops-level Live Deployment Validation (Deferred to Phase 7 Ops Validation):**
   - Independent multi-container startup, live fault-injection matrix, and live N-1 runtime compatibility checks are explicitly deferred to Phase 7 ops validation when independent container deployment infrastructure is executed.

**Consequences.** `P4-GATE` is marked `done` for the code-level cutover and architecture boundaries scope. Live container deployment validation remains tracked under Phase 7 operations verification.

**Affected Tasks:** P4-GATE (done), P5-SCHEMA-001 (ready).

