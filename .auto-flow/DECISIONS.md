# G-5 Decisions

## Centralize the console sider visual contract

- Context: The console sider mixed local shadcn utility styles, shared row styles, list-specific widths, colored category icons, and a separate user/dropdown treatment.
- Decision: Extend the existing `packages/sidebar-menu` CSS seam with Garden shell, row, group, list, user, and dropdown classes; make console `features/sider` consume those classes and remove local width/color overrides.
- Alternatives: A page-local stylesheet or a second sidebar component was rejected because both would preserve the existing style fork.
- Consequences: Console sidebar contexts share geometry and state treatment; the management app is not visually changed because it does not consume the new console shell classes.

## Locale-aware Garden profile seam

- Context: Console and Management both support `zh-CN` and `en-US`, but initial locale detection is duplicated, the landing switcher directly mutates vue-i18n, and the global design system has no locale-specific metric layer. Browser evidence showed English landing copy with `html lang="zh-CN"`.
- Decision: Use the existing semantic `html lang` attribute as the single profile selector. Add explicit zh/en typography and layout metric profiles to shared theme/design-system tokens; keep Garden colors and app density shared. Unify initial locale resolution and make every switch path pass through the existing locale lifecycle; seed the same marker in the pre-bundle bootstrap.
- Alternatives: Duplicate zh/en page stylesheets (rejected: creates a second design system); put theme imports inside locale-preference (rejected: wrong dependency direction); rewrite all translation strings to equal character counts (rejected: copy quality and i18n semantics are not layout contracts).
- Consequences: One locale change updates content and CSS custom properties through `html[lang]`; components remain locale-agnostic. CJK gets its own font/leading/space metrics while English keeps editorial Latin metrics. Density remains an independent app policy.
- Affected tasks: I18N-DESIGN-001, I18N-DESIGN-002, I18N-DESIGN-003, I18N-DESIGN-004.

## Services issue execution decisions (2026-08-28)

### Reuse the existing code-execution seam

- Context: `CodeExecutionPort` and serializable run DTOs already exist in `app-api`, while App injects the concrete Judge runtime implementation and Judge already hosts Dubbo.
- Decision: Keep `CodeExecutionPort` as the only Interface. Add a backend-judge provider and an App remote Adapter, inject the Interface in the controller, and activate the Docker implementation only for the Judge runtime. Preserve synchronous success semantics and map transport unavailability to a typed HTTP failure.
- Alternatives: Install Docker in App (rejected: expands the public host control plane); create another preview Interface (rejected: duplicate seam); disable `/run` globally (rejected: avoidable behavior loss).
- Consequences: The App caller learns one narrow Interface; Judge owns Docker execution. A dedicated no-retry execution timeout must be explicit because the 3-second write policy is shorter than sandbox execution.
- Affected tasks: SVC-001.

### Replace broad mutation and lookup Interfaces

- Context: Judge and Submission adapters implement broad contracts with normal methods that can only throw unsupported exceptions.
- Decision: Replace `SubmissionWritePort` with `SubmissionIntakePort` and `SubmissionVerdictWritePort`; replace Submission's use of `ProblemAdminReadPort` with `ProblemTitleLookupPort`. Migrate providers, callers, tests, and contract-shape gates; do not layer narrow wrappers over a still-published broad remote Interface.
- Alternatives: Default methods that throw (rejected: preserves the shallow Interface); a generic command envelope (rejected: hides type safety and expands scope).
- Consequences: Consumer-visible capability matches real behavior; SVC-004 can be audited against narrow bounded reads.
- Affected tasks: SVC-002, SVC-004.

### Deepen Admin aggregation without an event table

- Context: `DefaultAdminUserProjection` and `AdminUserEnricher` independently merge Auth and App responses and classify partial availability.
- Decision: Make `AdminUserEnricher` the single deep Module for cross-Owner merge/degradation; keep pagination, permissions, and local stats in `DefaultAdminUserProjection`.
- Alternatives: Add an Admin event read model now (rejected: no latency/availability trigger); create a new one-use gateway (rejected: existing Module already has the real seam and many callers).
- Consequences: Aggregation failure semantics gain Locality without schema, migration, or new infrastructure.
- Affected tasks: SVC-006.

### Preserve external production gates

- Context: SVC-003 requires 14-day traffic/drain/error-budget evidence; SVC-007..010 require real topology, threat model, traffic/drills, or release history. The checkout has only a development environment.
- Decision: Complete every repository-actionable predecessor and record the remaining items as external gates with exact unblock evidence. Do not label development tests as production acceptance.
- Alternatives: Build speculative HA/isolation/event/SLO infrastructure or fabricate observation history (rejected: violates the issue registry and current project scope).
- Consequences: The development branch reaches the strongest honest state; Objective closure is blocked only if the user interprets externally-triggered entries as requiring unavailable production evidence.
- Affected tasks: SVC-003-GATE, SVC-007-010-GATES.

## Architecture remediation execution decisions (2026-08-30)

### Treat the new directive as repository-cutover authority, not production-cutover evidence

- Context: the prior SVC-003 decision retained compatibility until external 14-day evidence; the new user directive explicitly authorizes repository implementation, migrations, tests, CI/CD, ADRs, and local commits while forbidding unapproved production actions.
- Decision: remove repository compatibility writers and legacy ownership only after new automated backfill/reconciliation/rollback proofs pass. Mark real traffic observation, real production migration, credential rotation, HA failover, and production deployment as external; never describe repository completion as production application.
- Alternatives: retain all compatibility code indefinitely (rejected by the explicit clean-cutover task); fabricate production evidence (forbidden); execute production actions without authority (forbidden).
- Consequences: the repository can converge to the target architecture and produce executable runbooks while production application remains separately gated.
- Affected tasks: P1-SUB-001 through P1-DATA-001, P2-MIG-001, CLOSURE-001.

### Reuse existing control planes and deep modules

- Decision: extend .auto-flow, services/docs/SERVICES_ISSUES.md, owner migration manifest, Streams/Inbox, Worker SLO, AdminUserEnricher, BackupProcessPort, and architecture gates. Do not create a parallel task system or replacement architecture.
- Affected tasks: all architecture_remediation_20260830 tasks.

### Isolate registry identities by workload

- Context: production registry clients previously inherited one shared Nacos username/password and the Compose override could silently run standalone.
- Decision: keep dev explicitly standalone in the dev namespace; require a production cluster peer list and non-empty namespace; provision one registry user/role per Dubbo workload with only config/service read-write permissions, while the built-in Nacos account stays disabled.
- Consequences: a service credential or registry permission can be rotated independently; live Nacos account provisioning and registration smoke remain environment-gated and are not performed by repository work.
- Affected tasks: P0-SEC-008, P3-IDENTITY-001.

### Route Admin rejudge through the Submission owner

- Context: Admin rejudge still had an App provider, a local Admin service fallback, and a legacy RejudgePolicy contract; that left authorization, generation fencing, and judge-task ownership split across services.
- Decision: Admin sends an authenticated `RejudgeCommand`/`BatchRejudgeCommand` only to group=`backend-submission`. The owner verifies the RS256 delegated assertion and Redis replay claim, atomically claims the command receipt, performs the generation/lease transition, and writes the non-shadow judge outbox in the owner transaction. Retain the notification boolean only for wire compatibility; notification delivery belongs to the later Notification/Audit event work.
- Consequences: duplicate successful commands replay without re-mutation, in-flight duplicates conflict, changed payloads conflict, stale terminal/Judging races fail or expire safely, and App no longer has a rejudge writer/provider. Live Nacos/Dubbo/Redis/MySQL registration and traffic proof remain external.
- Affected tasks: P1-SUB-001, P1-SUB-002, P1-SUB-003, P1-AUDIT-001.

### Make Submission backfill resumable and cutover-verifiable

- Context: the original Submission cutover copied all rows in one statement and required an empty owner target, so it could not resume a partial migration or explain a same-key owner conflict before revoking App grants.
- Decision: make dry-run the default; process each owner table in primary-key batches with a schema-bound checkpoint, append-only failure export, insert-only target writes, NULL-safe field conflict detection, and no overwrite of existing owner rows. Require explicit backfill/quiesce confirmations for writes. Gate cutover on zero source/target count, checksum, missing/extra-key, field, and writer differences; cutover no longer performs an implicit full-table copy.
- Consequences: a partial backfill resumes from the last completed boundary and a conflicting newer owner row fails closed with an actionable artifact. Disposable MySQL/Redis rehearsal is wired but remains environment-blocked until Docker access is available.
- Affected tasks: P1-SUB-003, P1-SUB-004, P1-DATA-001.

### Move Submission reconciliation to owner facts

- Context: App reconciliation still queried the Submission-owned `submissions` table, while the scheduled Admin scan had no multi-replica lease or explicit incremental watermark.
- Decision: expose grouped `SubmissionUserReferenceCountDTO` facts through `SubmissionReconciliationReadPort` with a 500-row page cap; run a nightly full scan and an explicit caller-watermarked incremental scan; use the existing owner boundaries and a connection-scoped MySQL advisory lock.
- Consequences: busy replicas return an unpersisted `SKIPPED`; invalid owner pages and lock/owner failures persist actionable `FAILED` records and increment metrics. App retains only a wire-compatible zero placeholder until the later contract-contraction task. Docker-backed integration remains external.
- Affected tasks: P1-SUB-004, P1-DATA-001, P1-AUDIT-001.

### Move Notification persistence and reconciliation to the Notification owner

- Context: App still carried Notification-owned persistence/reconciliation reads, while event delivery already had the Streams, Inbox, and ledger seams needed to keep intent publication separate from durable delivery.
- Decision: expose bounded full/incremental grouped Notification facts through `NotificationReconciliationReadPort`, validate pages in the Notification owner, and consume them from Admin through a `backend-notification` Dubbo adapter. Remove App Notification SQL and runtime implementations while retaining App intent publishing and WebSocket push relay seams.
- Consequences: Notification is the sole owner of notification rows, preferences, and delivery-ledger state; duplicate Redis events are absorbed by Inbox/idempotency, and Admin fails closed on unavailable, malformed, unordered, or oversized owner facts. Production observation and cutover remain external.
- Affected tasks: P1-NOT-001, P1-DATA-001, P1-AUDIT-001.

## P1-DATA-001: contract all normal Submission reads before physical contraction

- Decision: route normal App user, contest, Problem-statistics, user-tag, generation, and Admin Submission reads through provider-owned Submission facts; keep local mapper/projection adapters only behind explicit `legacy-rollback`.
- Decision: publish newly added user-stat and Problem-stat wire methods at Submission contract version `1.1.0`; keep the deprecated broad mutation contract/provider for its existing N-1 compatibility window.
- Decision: make difficulty aggregation one-way: App supplies its local problem-id/difficulty facts to Submission, and Submission returns owner-only counts without a reverse App RPC/N+1 loop.
- Decision: keep physical contraction separate from ordinary Flyway. The shared chain creates only durable `owner_contraction_proof`; the confirmation-, backup-, quiescence-, parity-, checksum-, and grant-gated contraction history performs the explicit legacy-table drop. Production migration and traffic authority remain external.
- Consequences: repository checks prove normal ownership and a disposable upgrade-shaped contraction, while `consumer_inbox`, `app_command_receipt`, applied migrations, and rollback authority remain preserved.
- Affected tasks: P1-SUB-004, P1-NOT-001, P1-DATA-001, P1-AUDIT-001.

## P1-AUDIT-001: move audit writes behind owner-local outboxes

- Context: Auth and App previously wrote the Admin schema's `audit_outbox` directly, leaving a cross-owner database write grant and putting event claim/dispatch state in the wrong Owner.
- Decision: Auth and App write local audit outboxes in their business transactions; their local dispatchers publish versioned `AuditRecorded` envelopes to `stream:integration`. Admin stages only accepted App/Auth events into the fixed `Admin-Audit` consumer inbox and inserts `audit_logs` idempotently by event id.
- Consequences: Redis/XADD failure is retryable through owner-local claim fencing; duplicate, disorder, malformed, and handler-failure paths use the existing inbox dedup/lease/retry/DEAD machinery. Forward Auth/App migrations create only local tables; a separate privileged post-owner migration revokes the historical Admin-table INSERT grants after all owner outboxes exist, so scoped owner migration accounts never need cross-owner privileges.
- Affected tasks: P1-AUDIT-001, P1-SEAM-001, P2-MIG-001.

## P2-MIG-001: execute ordered owner migrations in CD

- Context: production CD previously ran one shared Flyway chain and could not prove owner order, account/schema separation, concurrent-run handling, retry behavior, or rollback compatibility.
- Decision: use one host-locked manifest in the fixed order `shared -> auth -> admin -> app -> notification -> submission -> post-owner`; use a separate Submission migration account, bounded retry without automatic repair, secret-free JSON/human reports, and a post-owner privileged chain for cross-schema grant cleanup.
- Consequences: owner-scoped migrations remain limited to their own schemas, while baseline generation/adoption and local startup apply the same post-owner control; rollback is represented by `skip_migrations=true` and never performs schema downgrade. Production migration and remote deployment remain external.
- Affected tasks: P2-MIG-001, P2-BACKUP-001.

## P2-BACKUP-001: keep complete owner backup outside the Admin HTTP path

- Context: the Admin backup API and `BackupProcessPort` dump only the service datasource, while the target topology has five data owners and requires restore evidence.
- Decision: use an external Ops runbook to archive `ulticode` plus `auth`, `admin`, `app`, `notification`, and `submission`; encrypt with an operator-supplied 32-byte key, record secret-free archive/table/migration metadata, serialize with `flock`, and restore only into a disposable MySQL drill target.
- Consequences: checksum reconciliation, Flyway validation, smoke, retention, and measured RPO/RTO are executable locally; production off-host storage, key management, and restore authority remain external. The existing Admin HTTP backup surface remains compatible and is not expanded into a cross-owner business-data API.
- Affected tasks: P2-BACKUP-001, P3-LEASE-001, P2-OBS-001.

## P2-REDIS-001: materialize ACL policy outside Git with overlap rotation

- Context: the tracked `docker/redis/users.acl` was a generated hash snapshot, while replacing a bind-mounted file could leave a running Redis instance on the old inode and there was no executable overlap/rollback proof.
- Decision: mount an ignored runtime ACL directory, atomically rename generated hash-only `users.acl`, keep current/next credentials as two Redis password hashes during `prepare`, allow the dedicated ops principal to `ACL LOAD`, and expose `finalize`, `rollback`, and `drift-check` under one `flock`.
- Consequences: local startup and host deploy materialize without committing verifier hashes; disposable Redis proves old/new credential behavior and retention of the deny-by-default command/key policy. Production secret-store rotation, host ACL directory, and rollout authority remain external.
- Affected tasks: P2-REDIS-001, P2-TLS-001, P3-LEASE-001.

## P2-TLS-001: make HTTPS/HSTS a production profile without changing dev HTTP

- Context: both frontend gateways served only HTTP, HSTS was commented out, and the images had no certificate secret mount or executable HTTPS contract.
- Decision: keep dev on the existing port 8080 through an empty listener overlay; production mounts `TLS_CERT_DIR`, overlays an `8443 ssl` listener with TLS 1.2/1.3 and HTTP 301 redirect, maps HSTS from `$scheme` in the shared headers include, and checks HTTPS from container healthchecks.
- Consequences: Auth production explicitly requires Secure cookies and production CORS/frontend origins; resource owners use HTTPS Auth JWKS. Certificate files, domain/edge port ownership, and rotation remain external and are never committed.
- Affected tasks: P2-TLS-001, P0-SEC-004, P2-SC-001.

## P1-SEAM-001: prune dead contracts without collapsing real boundaries

- Context: the App API still contained unreferenced Follow ingestion/payload, Judge execution, and generic Achievement trigger types, while the live-ranking contract used a default method that only threw at runtime.
- Decision: remove only the production-unreferenced types and make the live-ranking page method abstract; retain contracts with concrete callers/providers, explicit health/rollback roles, or the documented Submission N-1 compatibility window.
- Consequences: the App API surface is smaller and no normal provider can silently compile while failing at runtime. Contract/API compatibility and affected clean reactor tests remain the guard; mixed-version external consumer inventory is still required before production rollout.
- Affected tasks: P1-SEAM-001, P2-MIG-001, ARCH-CONTRACT-001, ARCH-DUBBO-001.
