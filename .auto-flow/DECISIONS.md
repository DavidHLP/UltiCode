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
