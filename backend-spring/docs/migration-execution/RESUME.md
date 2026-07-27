# Migration Resume

Current Phase: Phase 2
Current Task: P2-AUTH-001-A (move refresh-token ownership into backend-auth)

Last Verified Commit:
- e3cc4c5 chore(migration): RESUME + WORKLOG after P2-AUTH-001-B closure
- ddf7faa chore(migration): mark P2-AUTH-001-B done; record commit 9b4aaf9
- 9b4aaf9 feat(auth): move JWT/CSRF plumbing into backend-auth (P2-AUTH-001-B)

Completed:
- 20 / 59 (Phase 0 gate + nine Phase 1 tasks + P2-AUTH-001-B done;
  P1-GATE closed).
- P2-AUTH-001 parent in_progress; A-G subtasks: B done, A/E next.

Blocked:
- None.

Current Work:
- P2-AUTH-001-B closed. backend-auth has its own JWT signing/verifying
  + CSRF components under com.ulticode.auth.security.* with its own
  AuthSecurityConfig (narrower than legacy) and AuthAuthenticationEntryPoint.
  17 unit tests added; full reactor ./mvnw verify -B PASS, 1795 tests,
  0 failures, 4 skipped.
- ADR-MIG-AUTH-JWT-PLACEMENT records the two-step placement: verify-only
  shared utility is owned by P2-AUTH-002 (extract to backend-common so
  App/Admin can offline-verify per guide §7.3/§11).
- next: P2-AUTH-001-A (refresh-token extraction) and P2-AUTH-001-E
  (RBAC/permission) — both have all dependencies satisfied after B.

Last Validation:
- ./mvnw verify -B full backend-spring reactor: PASS (41 s, 1795 tests,
  0 failures, 4 skipped).

Next:
1. P2-AUTH-001-A: refresh-token entity/mapper/service extraction.
2. P2-AUTH-001-E: RBAC/permission ownership (parallel with A; no shared files).
3. P2-AUTH-001-C: AuthController + session/account adapters (depends on E).
4. P2-AUTH-001-D: OAuth state and provider adapters (depends on C).
5. P2-AUTH-001-F: password reset and email (depends on C).
6. P2-AUTH-001-G: backend-auth standalone integration test suite.
7. P2-AUTH-002: resource-server JWT verifier in App/Admin (extract
   verify-only utility to backend-common per ADR-MIG-AUTH-JWT-PLACEMENT).
8. P2-AUTH-003: provider identity / authz version additive schema.
9. P2-AUTH-004: Gateway /auth/** cutover.
10. P2-RBAC-001: Auth-only role/permission writer; App/Admin read-only RPC.
11. P2-GATE: Phase 2 gate.

Dirty Worktree:
No — `git status` clean. Last 3 commits: 9b4aaf9 (B implementation),
ddf7faa (TASKS.yaml evidence), e3cc4c5 (RESUME+WORKLOG). The previous
"Dirty Worktree: Yes" line in this file was a stale snapshot that
survived the P2-AUTH-001 split commit (e50c84b) and is now corrected.

PUSH: NOT pushed. GitHub writes require explicit user approval. Local
ahead of origin/main: 106 commits.

Key references:
- backend-spring/docs/MICROSERVICE_MIGRATION_GUIDE.md
- backend-spring/docs/migration-execution/TASKS.yaml
- backend-spring/docs/migration-execution/COVERAGE.md
- backend-spring/docs/migration-execution/DECISIONS.md
- backend-spring/docs/migration-execution/WORKLOG.md
- backend-spring/docs/migration-execution/_tools/update_task.py
- scripts/dev/dubbo-nacos-smoke.sh
