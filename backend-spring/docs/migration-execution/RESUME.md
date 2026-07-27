# Migration Resume

Current Phase: Phase 2
Current Task: P2-AUTH-001-B (move JWT/security plumbing into backend-auth)

Last Verified Commit:
- e835c7d chore(migration): update RESUME after delegation

Completed:
- 19 / 59 (Phase 0 gate + nine Phase 1 tasks done; P1-GATE closed)
- P2-AUTH-001 in progress; B subtask accepted from vanished worker subagent.

Blocked:
- None.

Current Work:
- P2-AUTH-001-B picked up after P2Auth001B worker subagent exited without
  producing any backend-auth code changes.
- Strategy: copy legacy `com.ulticode.security.{jwt,csrf}.*` to
  `com.ulticode.auth.security.{jwt,csrf}.*` inside backend-auth so the auth
  service can independently sign/verify JWTs and run CSRF. backend-legacy
  keeps its own copies untouched (Strangler Fig dual-run).
- deviation recorded in DECISIONS.md (ADR-MIG-AUTH-JWT-PLACEMENT):
  verify-only shared utility will later move to backend-common so App/Admin
  can offline-verify (§7.3 / §11); P2-AUTH-002 owns that extraction.
- next: backend-auth AuthSecurityConfig (permitAll on /auth/**, /actuator/**,
  JWT filter chain) + AuthAuthenticationEntryPoint; unit tests for
  JwtTokenProvider sign/verify/expire/secret-validation and CsrfService
  generate/validate/clear.

Last Validation:
- ./mvnw verify -B full backend-spring reactor: PASS (39.1 s, all modules).
- ./mvnw -pl backend-auth -am -B verify: PASS (placeholder shell).

Next:
1. P2-AUTH-001-B: AuthSecurityConfig + AuthAuthenticationEntryPoint +
   unit tests for JwtTokenProvider / CsrfService; ./mvnw -pl backend-auth
   -am verify; commit.
2. P2-AUTH-001-A: refresh-token entity/mapper/service extraction (depends on B).
3. P2-AUTH-001-E: RBAC/permission ownership (depends on B).
4. P2-AUTH-001-C: AuthController + session/account adapters (depends on E).
5. P2-AUTH-001-D: OAuth state and provider adapters (depends on C).
6. P2-AUTH-001-F: password reset and email (depends on C).
7. P2-AUTH-001-G: backend-auth standalone integration test suite.
8. P2-AUTH-002: resource-server JWT verifier in App/Admin (extract verify-only
   utility to backend-common per ADR-MIG-AUTH-JWT-PLACEMENT).
9. P2-AUTH-003: provider identity / authz version additive schema.
10. P2-AUTH-004: Gateway /auth/** cutover.
11. P2-RBAC-001: Auth-only role/permission writer; App/Admin read-only RPC.
12. P2-GATE: Phase 2 gate.

Dirty Worktree:
No — `git status` clean. Last commit e835c7d incorporated all earlier
uncommitted files (TASKS.yaml + WORKLOG.md updates from P2-AUTH-001
split + delegation). The previous "Dirty Worktree: Yes" line in this file
was a stale snapshot that survived the P2-AUTH-001 split commit (e50c84b).

PUSH: NOT pushed. GitHub writes require explicit user approval.

Key references:
- backend-spring/docs/MICROSERVICE_MIGRATION_GUIDE.md
- backend-spring/docs/migration-execution/TASKS.yaml
- backend-spring/docs/migration-execution/COVERAGE.md
- backend-spring/docs/migration-execution/DECISIONS.md
- backend-spring/docs/migration-execution/WORKLOG.md
- backend-spring/docs/migration-execution/_tools/update_task.py
- scripts/dev/dubbo-nacos-smoke.sh
