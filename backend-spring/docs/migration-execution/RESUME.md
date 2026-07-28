# Migration Resume

Current Phase: Phase 3
Current Task: P3-DBPERM-001 Complete! All Phase 3 core tasks done. Next ready task: P3-GATE (Phase 3 gate validation).

Last Verified Commit:
- 72e6a40 feat(arch): implement per-owner DB user shadow grants and violation logging (P3-DBPERM-001)
- (HEAD: docs(migration) Phase 3 closure commit recording TASKS/RESUME/WORKLOG/COVERAGE evidence)
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
- Total tasks completed in TASKS.yaml: 88 / 112.

Blocked:
- None.

Current Work:
- P3-DBPERM-001 fully completed with full reactor verify PASS (1852 tests run, 0 failures, 0 errors, 4 skipped in 48.3s).
- Flyway V20260728213000 creates shadow DB users auth_rw / admin_rw / app_rw with per-owner table grants; zero hardcoded credentials, `${flyway:defaultSchema}` placeholder.
- DbOwnerWebHandlerInterceptor routes DbOwnerContext by table ownership (not endpoint audience): /auth|/users|/admin/users|/admin/account -> AUTH; /admin/settings|audit|dashboard|analytics|/moderation -> ADMIN; admin business endpoints -> APP (writes flow through P3-OWNER-001 App owner ports).
- audit_outbox treated as owner-neutral cross-domain integration seam (P3-AUDIT-001): append-only INSERT grants for auth_rw/app_rw, full grant for admin_rw dispatcher.
- DbOwnerViolationInterceptor logs WARN [DB_OWNER_VIOLATION] on cross-owner INSERT/UPDATE/DELETE; verified via Logback ListAppender capture and Testcontainers physical grant enforcement (MySQL error 1142).

Last Validation:
- ./mvnw verify -B full backend-spring reactor: PASS (1852 tests run, 0 failures, 0 errors, 4 skipped in 48.3s).
- ./mvnw -pl backend-legacy test -Dtest='DbOwnerWebHandlerInterceptorTest,DbOwnerViolationInterceptorTest' -B: PASS (42 tests run, 0 failures, 0 errors).
- ./mvnw -pl backend-legacy test -Dtest='DbOwnerPermissionIT,OwnerBoundaryArchTest' -B: PASS (12 tests run, 0 failures, 0 errors).

Dirty Worktree:
- No. Docs updates (TASKS.yaml / RESUME.md / WORKLOG.md / COVERAGE.md) committed in the Phase 3 closure docs commit; code committed per task (9378ec1 / 3421fde / 23b2ece / 72e6a40).

PUSH: NOT pushed. Per AGENTS.md GitHub Write Gate, push requires explicit user approval.

Key references:
- backend-spring/docs/MICROSERVICE_MIGRATION_GUIDE.md
- backend-spring/docs/migration-execution/TASKS.yaml
- backend-spring/docs/migration-execution/COVERAGE.md
- backend-spring/docs/migration-execution/DECISIONS.md
- backend-spring/docs/migration-execution/WORKLOG.md
