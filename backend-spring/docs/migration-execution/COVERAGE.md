# Migration Plan → Task Coverage

Source: `backend-spring/docs/MICROSERVICE_MIGRATION_GUIDE.md`
Truth: `backend-spring/docs/migration-execution/TASKS.yaml`

| Guide item | Task(s) | Status |
|---|---|---|
| §1.2 / 3.1 / 3.2 Three coarse services, Auth/Admin/App | Phase-2/3/4 tasks | pending |
| §5 Schema drift — `backups` migration missing | P0-SCHEMA-001 | ready |
| §5.1 `problem_notes` schema drift | P0-SCHEMA-002 | ready |
| §5.1 migration-only table inventory | P0-SCHEMA-003 | pending |
| §7.1 OAuth state cookie binding | P0-SEC-001 | ready |
| §7.1 OAuth provider identity / verified email | P0-SEC-002 | pending |
| §7.1 WS validator unification, active/ban, fail-closed | P0-SEC-003 | pending |
| §7.1 Effective permission expiry | P0-SEC-004 | pending |
| §8.3 judge outbox/fence/stream cutover plan | P0-JUDGE-001 | pending |
| §5 / Phase-0 Table owner manifest | P0-ARCH-001 | pending |
| §10 / Phase-0 ArchUnit baseline | P0-ARCH-002 | pending |
| Phase-0 gate | P0-GATE | pending |
| §10 Reactor pom / multi-module skeleton | P1-INFRA-001 | pending |
| §10.1 backend-common minimal | P1-INFRA-002 | pending |
| §6.2 Contract modules | P1-API-001 | pending |
| §6 Dubbo + Nacos registry wiring | P1-INFRA-003 | pending |
| §6.4 OpenTelemetry trace propagation | P1-OBS-001 | pending |
| §11.3 Nginx Gateway inventory + header cleanup | P1-INFRA-004 | pending |
| §Phase-1 Service shells | P1-INFRA-005 | pending |
| Phase-1 gate | P1-GATE | pending |
| §Phase-2 Auth extraction | P2-AUTH-001 | pending |
| §7.3 Resource server JWT verifier | P2-AUTH-002 | pending |
| §7.5 Provider identity / authz version | P2-AUTH-003 | pending |
| §Phase-2 Gateway /auth/** cutover | P2-AUTH-004 | pending |
| §7.5 Auth-only RBAC writer | P2-RBAC-001 | pending |
| Phase-2 gate | P2-GATE | pending |
| §Phase-3 Owner-owned Application APIs | P3-OWNER-001 | pending |
| §Phase-3 Account/Profile seam | P3-OWNER-002 | pending |
| §Phase-3 Search/Dashboard batch projection | P3-SEARCH-001 | pending |
| §8.3 Audit outbox seam | P3-AUDIT-001 | pending |
| §Phase-3 Per-Owner DB user shadow | P3-DBPERM-001 | pending |
| Phase-3 gate | P3-GATE | pending |
| §6.2 Contracts (auth/app) | P4-RPC-001 | pending |
| §6.4 / §6.5 RPC policy | P4-RPC-002 | pending |
| §Phase-4 Problem cutover | P4-CUTOVER-001 | pending |
| §Phase-4 Contest/Submission cutover | P4-CUTOVER-002 | pending |
| §Phase-4 Judge/WS cutover | P4-CUTOVER-003 | pending |
| Phase-4 gate | P4-GATE | pending |
| §5.2 Per-Owner schema/DB grants | P5-SCHEMA-001 | pending |
| §5.2 users vertical split | P5-USERPROFILE-001 | pending |
| §5.2 Reconciliation jobs | P5-RECONCILE-001 | pending |
| Phase-5 gate | P5-GATE | pending |
| §8.3 / §Phase-6 Outbox dispatcher | P6-OUTBOX-001 | pending |
| §Phase-6 Inbox dedup / ledger reclaim | P6-INBOX-001 | pending |
| §8.3 Result outbox | P6-RESULT-001 | pending |
| §Phase-6 Replay / DLQ tooling | P6-REPLAY-001 | pending |
| Phase-6 gate | P6-GATE | pending |
| §Phase-7 Legacy module removal | P7-LEGACY-001 | pending |
| §Phase-7 Drop unused contracts / dup JWT util | P7-LEGACY-002 | pending |
| §Phase-7 Contract DB migrations | P7-DB-001 | pending |
| §Phase-7 Runbooks / scripts / docs | P7-DOCS-001 | pending |
| §19 Final migration gate | P7-FINAL | pending |

## Checklist Mapping

§13.1 调研与门禁:
- [ ] 每张活跃表 Owner — P0-ARCH-001 (manifest) → enforced per-phase
- [ ] 跨模块 Mapper/Service import audit — P0-ARCH-002 (ArchUnit baseline)
- [ ] package 双向依赖 vs Bean 循环 — Phase-3 (ArchUnit)
- [ ] migration-only 表行数与写入核验 — P0-SCHEMA-003
- [ ] `backups` migration — P0-SCHEMA-001
- [ ] `problem_notes` ALTER — P0-SCHEMA-002
- [ ] OAuth state cookie binding — P0-SEC-001
- [ ] OAuth provider identity/verified email — P0-SEC-002
- [ ] WS validator unification, fail-closed — P0-SEC-003
- [ ] Effective permission expiry — P0-SEC-004
- [ ] judge outbox/fence/stream plan — P0-JUDGE-001

§13.2 工程与 Contract:
- [ ] Reactor POM — P1-INFRA-001
- [ ] backend-common minimal — P1-INFRA-002
- [ ] provider-owned backend-auth-api, backend-app-api — P1-API-001, P4-RPC-001
- [ ] UUID/commandId/trace/deadline — P1-API-001
- [ ] Retry/timeout defaults — P4-RPC-002
- [ ] Consumer contract test — P4-RPC-001
- [ ] ArchUnit forbids impl-dependency, cross-Owner Mapper — P0-ARCH-002, P3-OWNER-001

§13.3 Gateway 与安全:
- [ ] Gateway /auth/** /admin/** /moderation/** routes — P1-INFRA-004
- [ ] Strip client identity headers — P1-INFRA-004
- [ ] Local JWT verifier per service — P2-AUTH-002
- [ ] JWKS / asymmetric key — Phase 2 (future; see DECISIONS)
- [ ] alg/iss/aud/typ/kid/exp/nbf — P2-AUTH-002
- [ ] Refresh only in Auth, hash-only + CAS + family — P2-AUTH-001
- [ ] Browser CSRF separate from service bearer — P2-AUTH-002
- [ ] /admin/** + method security — Phase 3/4 (covered by existing @PreAuthorize)
- [ ] Dubbo service principal vs end-user delegation — P4-RPC-002

§13.4 数据与事务:
- [ ] Admin not direct-writing App/Auth tables — P3-OWNER-001
- [ ] Moderation decomposed — P3-OWNER-001
- [ ] users vertical split — P5-USERPROFILE-001
- [ ] submissions+judge_outbox not split — P5-SCHEMA-001
- [ ] Per-service DB user/grant — P5-SCHEMA-001
- [ ] expand/backfill/checksum/shadow — P5-USERPROFILE-001
- [ ] Event envelope metadata — P6-OUTBOX-001
- [ ] Outbox same-tx, inbox dedup — P6-OUTBOX-001, P6-INBOX-001
- [ ] Notification/email ledger reclaim — P6-INBOX-001
- [ ] Subscription active unique + status CAS — Phase 3 (subscription module)
- [ ] Object storage temp object + finalize — Phase 5/7 (later)
- [ ] No Seata — DECISIONS.md ADR

§13.5 基础设施与可观测:
- [ ] Nacos registry only — P1-INFRA-003
- [ ] Dubbo Triple timeout/version/health — P4-RPC-002
- [ ] OpenTelemetry chain — P1-OBS-001
- [ ] Prometheus scrape — Phase 1 wiring
- [ ] Outbox/inbox/lease metrics — P6-OUTBOX-001, P6-INBOX-001
- [ ] Redis prefix/credential/eviction — Phase 4/5
- [ ] WS sticky/broadcast/relay — P4-CUTOVER-003
- [ ] Scheduled jobs owner/flag/lease — Phase 3/4
- [ ] Compose base/prod no public infra ports — P1-INFRA-004, P7-DOCS-001
- [ ] Backup recovery drill — P5-RECONCILE-001, P7-DOCS-001

§13.6 切流、验证与回滚:
- [ ] Per-route canary — P4-CUTOVER-001..003
- [ ] ./mvnw verify -B per phase gate
- [ ] Login/OAuth/refresh/Admin/Problem/Submission/Judge/Contest/WS E2E — Phase 2/4
- [ ] Inject Auth/App/Admin/Nacos/Redis/SMTP/net failures — Phase 4
- [ ] Replay test for write timeout — P6-OUTBOX-001, P6-RESULT-001
- [ ] Old service starts on additive schema — every Phase Gate
- [ ] No reverse migration for rollback — every Phase Gate
- [ ] One business cycle before deleting old route/col — Phase 7
- [ ] full/integration + Compose dev/prod config + backup + replay before legacy delete — P7-FINAL
- [ ] Update scripts/deploy/runbook/guide — P7-DOCS-001

## Risk Mapping (R1–R20)
| Risk | Mitigation tasks |
|---|---|
| R1 Distributed monolith | P0-ARCH-002, P3-OWNER-001, P5-SCHEMA-001 |
| R2 RPC chains | P4-RPC-002 (single-hop enforcement) |
| R3 Auth SPOF / key leak | P2-AUTH-002, P2-AUTH-003 |
| R4 OAuth defects migrated | P0-SEC-001, P0-SEC-002 |
| R5 Shared writes to users | P2-RBAC-001, P3-OWNER-002, P5-USERPROFILE-001 |
| R6 Distributed tx / dual-write loss | P0-JUDGE-001, P6-OUTBOX-001, P6-INBOX-001, P6-RESULT-001 |
| R7 RPC retry double-write | P4-RPC-002 |
| R8 Contract incompatibility | P4-RPC-001, P1-API-001 |
| R9 Schema split reconciliation | P5-USERPROFILE-001, P5-RECONCILE-001 |
| R10 WS multi-instance / ban propagation | P0-SEC-003, P4-CUTOVER-003 |
| R11 Judge duplicate/lost | P0-JUDGE-001, P6-RESULT-001 |
| R12 Audit actor lost | P3-AUDIT-001 |
| R13 Schema drift | P0-SCHEMA-001, P0-SCHEMA-002 |
| R14 Local files / backup | P5-SCHEMA-001 (backup grants) |
| R15 Dev complexity | P1-INFRA-005 |
| R16 Migration race | P5-SCHEMA-001 (per-schema history) |
| R17 Redis single domain | Phase 4/5 (config-side) |
| R18 Premature MQ/Sentinel/Config | DECISIONS.md ADR-MIG-MQ (deferred) |
| R19 Education template drift | DECISIONS.md ADR-MIG-DOMAIN (no Course/Teacher) |
| R20 Fine-grained permission confusion | P0-SEC-004 |

## Coverage Audit

Run at every Phase Gate. Check:

1. Every Phase plan bullet maps to ≥ 1 task.
2. Every task has an Acceptance Criterion mapping to a checklist item.
3. No task has empty acceptance_criteria.
4. Status of every Phase task is `done` or `superseded` before declaring
   its Phase complete.