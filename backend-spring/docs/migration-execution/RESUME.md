# Migration Resume

Current Phase: Phase 4
Current Task: P4-GATE (in_progress) — Phase 4 gate validation (requires live stack/RPC trace/fault-injection evidence)

Last Verified Commit:
- 81c1756 feat(user): add DEFAULT 'USER' migration and enforce via ArchUnit (P2-DISC-005)
- f3bbebf / 3c1246b feat(cutover): implement WS multi-instance broadcast bridge + allowlist security fix (P4-CUTOVER-005)
- 4c195f7 feat(cutover): implement ContentModeration Provider + Forum/Solution routing (P4-CUTOVER-004)
- 608e341 feat(cutover): implement Notification admin + batch rejudge Dubbo providers (P4-CUTOVER-003)
- 574cda3 feat(cutover): implement Contest/Submission Dubbo providers + routing (P4-CUTOVER-002)
- 3db1fcc feat(cutover): implement Problem Dubbo provider + feature-flagged routing (P4-CUTOVER-001)
- 5c4d1e3 feat(rpc): enforce single-hop chain + timeout/retry/idempotency policy (P4-RPC-002)
- 219e256 feat(api): add ContestAdministrationService + SubmissionAdministrationService contracts (P4-RPC-001)
- b9c66db fix(audit): move AuditOutboxMapper to admin.outbox.mapper for scan coverage (P3-AUDIT-001 follow-up)
- 51af2a0 refactor(problem): burn down frozen admin foreign-mapper write violations (P3-BURNDOWN-001)
- 72e6a40 feat(arch): implement per-owner DB user shadow grants and violation logging (P3-DBPERM-001)
- 23b2ece feat(audit): implement intra-JVM audit outbox seam and async fan-out dispatcher (P3-AUDIT-001)
- 3421fde test(admin): add dashboard stats projection coverage (P3-SEARCH-001)
- 9378ec1 refactor(user): enforce account/profile owner port writes for users table (P3-OWNER-002)
- d764ecf feat(arch): add p3_owner_001_f_admin_must_not_call_foreign_mapper_writes hard rule (P3-OWNER-001-F)

Completed:
- P3-OWNER-001 (Phase 3 — owner-owned Application APIs) and all subtasks A-G landed, verified, and closed.
- P3-OWNER-002 (Account/Profile port seam for `users`) landed, verified, and closed.
- P3-SEARCH-001 (Batch projection seam for Search / Dashboard) landed, verified, and closed.
- P3-AUDIT-001 (Audit outbox seam - intra-JVM) landed, verified, and closed.
- P3-DBPERM-001 (Per-Owner DB user shadow + violation logging) landed, verified, and closed.
- P3-BURNDOWN-001: burned down the 8 frozen admin foreign-mapper write violations (AdminTestCaseService x5 TestCaseMapper, ProblemImportServiceImpl x3 ProblemMapper) via new TestCaseOwnerPort + ProblemOwnerPort import methods; refroze p3_owner_001_f with an empty store (da138919).
- P3-GATE: Phase 3 gate CLOSED. verify 1863/0, ArchUnit 8/8, zero write violations. Found+fixed one production-startup regression (AuditOutboxMapper placed outside @MapperScan path → full-context NoSuchBeanDefinitionException; b9c66db). IT report: 65 pass / 13 fail, all 13 environment-only (Testcontainers Redis AUTH mismatch, sandbox namespace/seccomp fixtures).
- P4-RPC-001: completed backend-app-api Dubbo provider surface per §4.3 — added ContestAdministrationService (5 lifecycle methods) and SubmissionAdministrationService (rejudge). Contract design decisions recorded in ADR-P4-RPC-001.
- Total tasks completed in TASKS.yaml: Phase 3 fully done; P4-RPC-001 done, P4-RPC-002 ready.
- P4-CUTOVER-005: WS multi-instance broadcast bridge implemented via WebSocketBroadcastBridge + Redis Pub/Sub relay + closed WebSocketPayloadKind allowlist; 54/54 WS tests PASS including malicious gadget payload drop regressions.
- P2-DISC-005: Added Flyway migration V20260729103000__Add_Default_User_Role.sql for users.role DEFAULT 'USER', dropped placeholder write in UserManagementServiceImpl, enforced via ArchUnit rule p2_disc_005_forbid_direct_user_role_setter_calls (9/9 ArchUnit rules green).

Blocked:
- None (code-health). Two environment/test-fixture follow-ups recorded (not gate blockers): (1) Testcontainers Redis AUTH config mismatch; (2) sandbox ITs need seccomp-profile.json fixture + privileged runtime.

Current Work:
- Phase 3 complete. P3-GATE closed with code-health PASS and an honest IT report.
- P3-BURNDOWN-001 established a sibling TestCaseOwnerPort (problem-domain) rather than overloading ProblemOwnerPort for test_cases rows (separate table → sibling port preserves module cohesion); import row defaults + PartialUpdate null-skip semantics moved into DefaultProblemOwnerPort.
- P4-RPC-002: enforced single-hop RPC chain prevention (ArchUnit) + centralized timeout/retry/idempotency policy (RpcPolicy) + consumer defaults in all service YAMLs.
- P4-CUTOVER-001: ProblemAdministrationProvider + ProblemCutoverService (feature-flagged dual-path; bulk/moderation paths deferred to CUTOVER-002).
- P4-CUTOVER-002: ContestAdministrationProvider + SubmissionAdministrationProvider + dual-path adapters.
- P4-CUTOVER-004 (Forum/Solution/ContentModeration cutover) created from P4-CUTOVER-002 audit — pending, depends on ContentModerationProvider impl.
- Next: P4-CUTOVER-003 (Judge/WS families cutover) is ready.

Last Validation:
- ./mvnw verify -B full backend-spring reactor: PASS (1921 tests run, 0 failures, 0 errors, 4 skipped).
- ./mvnw -pl backend-legacy test -Dtest=OwnerBoundaryArchTest -B: 9/9 green.
- ./mvnw -pl backend-legacy test -Dtest='*WebSocket*' -B: 54/54 green.

Dirty Worktree:
- No. Code committed per task (51af2a0 / b9c66db); docs updates pending this commit.

PUSH: NOT pushed. Per AGENTS.md GitHub Write Gate, push requires explicit user approval.

Key references:
- backend-spring/docs/MICROSERVICE_MIGRATION_GUIDE.md
- backend-spring/docs/migration-execution/TASKS.yaml
- backend-spring/docs/migration-execution/COVERAGE.md
- backend-spring/docs/migration-execution/DECISIONS.md
- backend-spring/docs/migration-execution/WORKLOG.md
