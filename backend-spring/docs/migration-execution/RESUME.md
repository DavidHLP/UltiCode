# Migration Resume

Current Phase: Phase 5 (P4-GATE closed)
Current Task: P5-USERPROFILE-001 (ready) — Vertical split of `users` into account + profile

Last Verified Commit:
- 9c8f2b2 / 99283c2 feat(schema): create per-owner schemas auth/admin/app and restrict DB user grants (P5-SCHEMA-001)
- c8f40b6 / c2d3e30 feat(gate): close Phase 4 Gate + ADR-P4-GATE-AUDIT (P4-GATE)
- 7071e1d docs(migration): record ADR-P2-DISC-004 for MySQL 9.1 DDL portability (P2-DISC-004)
- ea2538f refactor(permission): remove legacy assignPermission/revokePermission methods (P2-DISC-006)
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
- P2-DISC-006: PermissionService interface shrunk to read-only queries; legacy assignPermission/revokePermission methods and unused write helpers removed; enforced via ArchUnit rule p2_disc_006_forbid_direct_user_permission_mapper_imports (9/9 ArchUnit rules green).
- P3-OWNER-001 & P3-OWNER-001-G: Completed integration validation and final review for owner write boundary; all subtasks A-G verified and closed.
- P2-DISC-004: Recorded ADR-P2-DISC-004 in DECISIONS.md choosing Option A for MySQL 9.1 DDL portability (future migrations use plain DDL syntax; V20260727021915 immutable per AGENTS.md); retained disposable-verify/ as documented regression test fixture per AC2 option (b).
- P4-GATE: Phase 4 Gate CLOSED with ADR-P4-GATE-AUDIT documenting code-level verification (three services, single-hop RPC, timeout/retry policies, 0 shared Mapper jars, contract tests) vs ops-level live deployment scope.
- P5-SCHEMA-001: Created `auth`, `admin`, `app` per-owner schemas via Flyway migration V20260729140000; revoked legacy default-schema grants; enforced DB user grants (auth_rw -> auth.*, admin_rw -> admin.*, app_rw -> app.*); verified with PerOwnerSchemaGrantTest (15/15 PASS, 1936 reactor tests PASS).

Blocked:
- None (code-health). Two environment/test-fixture follow-ups recorded (not gate blockers): (1) Testcontainers Redis AUTH config mismatch; (2) sandbox ITs need seccomp-profile.json fixture + privileged runtime.

- Phase 5 in progress. P5-SCHEMA-001 completed.
- Next: P5-USERPROFILE-001 (Vertical split of `users` into account + profile) is ready.

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
