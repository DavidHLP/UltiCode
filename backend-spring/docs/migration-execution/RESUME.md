# Migration Resume

Current Phase: Phase 2
Current Task: P2-AUTH-001-E (move RBAC/permission ownership into backend-auth)

Last Verified Commit:
- 7e6cedb chore(migration): mark P2-AUTH-001-A done; record commit da6f598
- 60ea58d chore(migration): record P2-AUTH-001-A closure + discovered P2-DISC-001/002
- da6f598 feat(auth): move refresh-token ownership into backend-auth (P2-AUTH-001-A)
- 9b4aaf9 feat(auth): move JWT/CSRF plumbing into backend-auth (P2-AUTH-001-B)

Completed:
- 21 / 61 (Phase 0 gate + nine Phase 1 tasks + P2-AUTH-001-A + P2-AUTH-001-B).
- Discovered follow-ups filed: P2-DISC-001 (BusinessException/ErrorCode
  promotion), P2-DISC-002 (Clock/TimeSource bean promotion).
- P2-AUTH-001 parent in_progress; A,B done; E next (no dependency on A).

Blocked:
- None.

Current Work:
- P2-AUTH-001-A closed. backend-auth now owns:
  - AuthErrorCode (9 AUTH 1xxxx constants, byte-identical to legacy)
  - AuthBusinessException (accepts NamespacedErrorCode)
  - AuthClockConfig (Clock bean for RefreshTokenService)
  - RefreshToken entity/mapper/service
  - 4 unit tests (RefreshTokenServiceTest)
- P2-AUTH-001-B closed earlier. backend-auth owns JwtTokenProvider,
  JwtProperties, JwtAuthenticationFilter, CsrfService, CsrfValidationFilter,
  AuthSecurityConfig, AuthAuthenticationEntryPoint; 17 unit tests.
- Next: P2-AUTH-001-E (RBAC/permission ownership) — PermissionService,
  PermissionVocabulary, UserPermission, RolePermission, mappers.
  Depends on B only (A and E are independent post-B).

Last Validation:
- ./mvnw verify -B full backend-spring reactor: PASS (41 s, 1795 tests,
  0 failures, 4 skipped).

Next:
1. P2-AUTH-001-E: RBAC/permission ownership into backend-auth.
2. P2-AUTH-001-C: AuthController + session/account adapters (depends on E).
3. P2-AUTH-001-D: OAuth state and provider adapters (depends on C).
4. P2-AUTH-001-F: password reset and email (depends on C).
5. P2-AUTH-001-G: backend-auth standalone integration test suite.
6. P2-AUTH-002: resource-server JWT verifier in App/Admin (extract
   verify-only utility to backend-common per ADR-MIG-AUTH-JWT-PLACEMENT).
7. P2-AUTH-003: provider identity / authz version additive schema.
8. P2-AUTH-004: Gateway /auth/** cutover.
9. P2-RBAC-001: Auth-only role/permission writer; App/Admin read-only RPC.
10. P2-GATE: Phase 2 gate.
11. P2-DISC-001 / P2-DISC-002: BusinessException + Clock/TimeSource
    bean promotions to backend-common (per ADRs).

Dirty Worktree:
No — `git status` clean. Local ahead of origin/main: 109 commits.

PUSH: NOT pushed. GitHub writes require explicit user approval.

Key references:
- backend-spring/docs/MICROSERVICE_MIGRATION_GUIDE.md
- backend-spring/docs/migration-execution/TASKS.yaml
- backend-spring/docs/migration-execution/COVERAGE.md
- backend-spring/docs/migration-execution/DECISIONS.md
- backend-spring/docs/migration-execution/WORKLOG.md
- backend-spring/docs/migration-execution/_tools/update_task.py
- scripts/dev/dubbo-nacos-smoke.sh
