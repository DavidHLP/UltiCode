# Migration Resume

Current Phase: Phase 4 (P3-GATE closed)
Current Task: P4-RPC-001 (ready) — implement provider-owned Contracts (Auth/App)

Last Verified Commit:
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
- Total tasks completed in TASKS.yaml: Phase 3 fully done; P4-RPC-001 now ready.

Blocked:
- None (code-health). Two environment/test-fixture follow-ups recorded (not gate blockers): (1) Testcontainers Redis AUTH config mismatch; (2) sandbox ITs need seccomp-profile.json fixture + privileged runtime.

Current Work:
- Phase 3 complete. P3-GATE closed with code-health PASS and an honest IT report.
- P3-BURNDOWN-001 established a sibling TestCaseOwnerPort (problem-domain) rather than overloading ProblemOwnerPort for test_cases rows (separate table → sibling port preserves module cohesion); import row defaults + PartialUpdate null-skip semantics moved into DefaultProblemOwnerPort.
- Next: P4-RPC-001 (provider-owned Contracts for Auth/App) is ready.

Last Validation:
- ./mvnw verify -B full backend-spring reactor: PASS (1863 tests run, 0 failures, 0 errors, 4 skipped).
- ./mvnw -pl backend-legacy test -Dtest=OwnerBoundaryArchTest -B: 8/8 green, freeze store refrozen empty (da138919).
- ./mvnw -Dtest='*IT' test -B: 65 pass / 13 fail (all environment/infrastructure-only, not code regressions).

Dirty Worktree:
- No. Code committed per task (51af2a0 / b9c66db); docs updates pending this commit.

PUSH: NOT pushed. Per AGENTS.md GitHub Write Gate, push requires explicit user approval.

Key references:
- backend-spring/docs/MICROSERVICE_MIGRATION_GUIDE.md
- backend-spring/docs/migration-execution/TASKS.yaml
- backend-spring/docs/migration-execution/COVERAGE.md
- backend-spring/docs/migration-execution/DECISIONS.md
- backend-spring/docs/migration-execution/WORKLOG.md
