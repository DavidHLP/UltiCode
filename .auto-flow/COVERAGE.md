# G-5 Coverage

| Requirement | Evidence |
| --- | --- |
| One Garden contract for shell, user row, nav rows, groups, list rows, actions, sub-items, and menus | `packages/sidebar-menu/src/styles/sidebar-menu.css`; console `features/sider` callers; `/problemset` browser snapshot |
| Preserve routes, permissions, activation, collapse, dropdown, and focus behavior | `SidebarNav.vue` logic unchanged; `AppSidebar.vue` context selection unchanged; console 70 files / 590 tests passed |
| Cover problemset, forum, contest, and personal context selection | `AppSidebar.vue` selects existing context data; `/problemset`, `/forum`, and `/contest` browser QA passed |
| Keep docs aligned | `packages/sidebar-menu/README.md`; `PROJECT_DOCUMENTATION.md` §9 |

Scope intentionally excludes the separate right-column `ProblemSetSidebar` and management visual redesign; management type-check/build smoke passed because the new selectors are console-class scoped.

## Locale-aware Garden design profiles

| Requirement | Task / Evidence |
| --- | --- |
| zh-CN and en-US have explicit global design profiles for typography, spacing, layout, and component metrics | I18N-DESIGN-002; `packages/theme/src/typography.css`, `packages/design-system/style.css` |
| Locale switching updates i18n, persistence, DOM language, and the active design profile together | I18N-DESIGN-001; shared locale-preference, both app callers, pre-bundle bootstrap, landing switch regression |
| Console, management, shared primitives, and landing shell consume the same profile seam without duplicate page systems | I18N-DESIGN-003; app shell and landing alias consumers |
| Both locales preserve translation key coverage and responsive behavior | I18N-DESIGN-004; console/management validators, tests, builds, desktop and narrow browser QA |

Out of scope: rewriting the 1908-key translation trees solely to equalize string length; changing Garden color semantics; adding RTL; release, deploy, or remote mutations.

Final gate: `./scripts/dev/verify-garden-design.sh --with-build` passed. Browser evidence covered desktop and 390px responsive behavior; cross-route switching verified the content language and computed locale metrics together. The shared package formatter is not declared at the repository root; app-scoped ESLint/formatter checks and `git diff --check` passed without rewriting package baselines.

## Services issues 2026-08-28

| Source item | Task / required evidence |
| --- | --- |
| SVC-001 App/Judge Docker execution seam | SVC-001; narrow CodeExecutionPort, Judge provider/App Adapter, success+unavailable regression, dependency/architecture checks |
| SVC-002 broad Submission/Problem contracts | SVC-002; intake/verdict and title-lookup Interface split, all caller/provider/test migrations, no unsupported normal Contract methods |
| SVC-003 Submission dual writer retirement | SVC-003-GATE; 14-day observation, drain, checksum, error-budget, and verified rollback evidence; external until real environment exists |
| SVC-004 App/Submission sync dependency ring | SVC-004 after SVC-002; immutable intake facts, narrow bounded-batch read, no speculative event projection |
| SVC-005 Search release/rollback selection | SVC-005; deploy option, rollback whitelist/all, services-matrix drift gate |
| SVC-006 Admin event user read model | SVC-006; first merge existing aggregation into AdminUserEnricher deep Module; event table remains metric-triggered |
| SVC-007 production multi-host HA | SVC-007-010-GATES; external topology/SLO trigger preserved |
| SVC-008 Judge node isolation | SVC-007-010-GATES; external multi-tenant/threat-model trigger preserved |
| SVC-009 operational observability evidence | SVC-007-010-GATES; requires real production traffic/drills; repository wiring remains audited |
| SVC-010 mixed-version history | SVC-007-010-GATES; requires real releases; contract gate remains enforced |
| CLOSED history | SVC-VALIDATE; regression/architecture gates and source evidence remain intact |
| ACCEPTED decisions | All tasks; no access-token writer, no new MQ/mesh/Kubernetes/Seata, Search manifest policy preserved, SubmissionFactsSnapshot remains minimal, compatibility seam retained until SVC-003 gate |
| Maintenance rules | SVC-VALIDATE; one registry, evidence-backed status transitions, docs/runbooks/contracts keep their own content |

Delivery authority excludes commit, push, merge, release, deploy, production data, and third-party mutation.

### Final Services gate

- Repository-actionable: SVC-001 CLOSED, SVC-002 CLOSED, SVC-004 ACCEPTED, SVC-005 CLOSED, SVC-006 deep-Module precondition complete, SVC-009 OTLP repository wiring complete.
- External/deferred by registry: SVC-006 event projection metrics, SVC-007 multi-host/SLO, SVC-008 multi-tenant threat model, SVC-009 real traffic/SLO/live drills, SVC-010 real mixed-version releases.
- Objective blocker: SVC-003 local-copy retirement lacks the required 14-day write/fence/read observation, zero local activity, real drain/error budgets/checksums, and verified target rollback.
- Formal Review: Standards 0, Spec 0, Security 0 Confirmed Findings.
- Validation: reactor verify 2714 tests; `*IT` 233 tests; zero failures/errors. Supported quick, N-1 compatibility, architecture/docs/DevStack, Compose dev/prod, YAML, graph, coverage and diff gates passed.
- Delivery: verified dirty worktree, no commit/push/deploy authorization.

## Architecture remediation 2026-08-30

The complete finding-to-task matrix is maintained in docs/architecture/remediation-traceability.md. The canonical finding text and lifecycle remain in services/docs/SERVICES_ISSUES.md; this coverage section links execution rather than duplicating the registry.

- P0 browser and internal security: P0-SEC-001..008 and ARCH-SEC-001.
- P1 owner cutovers and data seams: P1-SUB-001..004, P1-NOT-001, P1-DATA-001, P1-AUDIT-001, P1-SEAM-001.
- P2 production control plane: P2-MIG-001, P2-BACKUP-001, P2-REDIS-001, P2-TLS-001, P2-SC-001, P2-OBS-001, P2-DEPLOY-001.
- P3 resilience and HA: P3-SCHED-001, P3-LEASE-001, P3-GRACE-001, P3-RES-001, P3-STREAM-001, P3-SCALE-001, P3-HA-001, P3-IDENTITY-001, P3-NET-001, P3-JUDGE-001.
- Architecture/testing/closure: ARCH-CONTRACT-001, ARCH-DUBBO-001, TEST-COV-001, REVIEW-001, REVIEW-002, CLOSURE-001.
- Explicit ADR deferrals: Kubernetes, Service Mesh, Kafka, Seata, further App service split, and five independent database clusters.

### P2-SC-001 immutable signed image supply chain

- Production Dockerfile bases and Compose infrastructure images are digest-pinned; nine deployable services require an explicit digest manifest.
- Every external GitHub Action is pinned to a full commit SHA; Docker Publish emits BuildKit SBOM/provenance, a pinned Trivy report, Cosign signature/attestations, and an immutable release manifest.
- Deploy and rollback validate the manifest locally and run digest/signature/SPDX/SLSA/Trivy verification on the target host before Compose pull/up. Exceptions require a non-empty ignorefile and future UTC expiry.
- Contract, synthetic Compose config, three representative container builds, architecture/docs/YAML/shell/diff, and Graphify passed. Real registry/OIDC, promotion, and production host authority remain external.

### P2-OBS-001 runnable observability control plane

- The opt-in overlay wires owner Actuator metrics, web-less worker OTLP metrics, HTTP/Dubbo/Streams traces, mounted logs, Tempo/Loki correlation, Grafana dashboards, Prometheus rules, Alertmanager routing, and release annotations.
- Initial availability/latency/worker/reconciliation/backup/stream/security/scheduler/JVM/pool formulas, windows, budgets, and recovery actions are documented; production telemetry storage, notification, threshold tuning, and real-traffic SLO evidence remain external.
- Prometheus/Alertmanager/Collector validation, merged Compose, disposable overlay smoke, Search/Judge compile, Search Docker build, architecture/docs/YAML/XML/shell/diff, and Graphify passed.

### P2-DEPLOY-001 release rollback and config integrity

- Pre-mutation deployment checks bind the source commit, canonical migration manifest checksum, required files, merged production Compose config, immutable image evidence, and rollback schema compatibility.
- `deployment-integrity.sh` atomically records a secret-free `PENDING_HEALTH`/`HEALTHY`/`FAILED` descriptor; host-health checks HTTPS and every allowlisted service and never returns system success for partial health.
- The disposable contract covers descriptor JSON, schema mismatch refusal, atomic health updates, preflight ordering, and no remote mutation; full architecture/docs/YAML/shell/diff and Graphify passed.

### P3-SCHED-001 isolated scheduler executors

- Admin audit/reconciliation/backup, Submission outbox/recovery, and Search consume/heartbeat have explicit bounded owner-local schedulers with configurable 1–16 limits, 30-second graceful shutdown, and rejection/active/queued/completed metrics.
- The real scheduler test proves a blocked backup executor does not starve reconciliation and that closed executors reject work; static contracts cover all affected bindings and forbid unbounded scheduled executors.
- Admin/Submission/Search compile, scheduler/architecture/docs contracts, YAML/shell/diff checks, and Graphify passed; production saturation and SIGTERM drain remain follow-up environment evidence.
