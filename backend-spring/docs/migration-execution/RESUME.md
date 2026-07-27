# Migration Resume

Current Phase: Phase 2
Current Task: P2-AUTH-003 (blocked on credential exposure incident)

Last Verified Commit:
- c2386f0 feat(gateway): cut over /api/auth/ route family to backend-auth service (P2-AUTH-004)
- 9c63135 feat(sec): install offline resource server JWT verifiers in App and Admin (P2-AUTH-002)
- 169b7e4 feat(auth): complete backend-auth standalone extraction (P2-AUTH-001 & P2-AUTH-001-G)
- ac47294 feat(auth): move password reset and email integration into backend-auth (P2-AUTH-001-F)
- 2b8447e feat(auth): move OAuth state and provider adapters into backend-auth (P2-AUTH-001-D)
- 90030e6 feat(auth): move AuthController and session/account adapters into backend-auth (P2-AUTH-001-C)
- 409d615 feat(auth): move RBAC and permission ownership into backend-auth (P2-AUTH-001-E)
- 02e2ad8 fix(sec): add .env patterns to .gitignore (P2-SEC-HYGIENE-002)
- 96daeab chore(migration): record non-sensitive credential exposure incident
- 6d65f59 chore(migration): mark P2-AUTH-003 blocked on credential exposure incident
- 133ae48 feat(db): add authz_version + session-family columns (P2-AUTH-003 EXPAND phase)
- 2af78d4 chore(migration): mark P2-AUTH-003 done with EXPAND-phase evidence
  (reverted to in_progress then blocked — see WORKLOG)
- 5b2b677 chore(migration): record P2-DISC-001/002/003 deferred cross-cutting promotions
- da6f598 feat(auth): move refresh-token ownership into backend-auth (P2-AUTH-001-A)
- 9b4aaf9 feat(auth): move JWT/CSRF plumbing into backend-auth (P2-AUTH-001-B)

Completed:
- 33 / 66 (Phase 0 gate + nine Phase 1 tasks + P2-AUTH-001-A +
  P2-AUTH-001-B + P2-SEC-HYGIENE-001 + P2-COV-AUDIT-001 +
  P2-DONE-EVIDENCE-AUDIT-001 + P2-SEC-HYGIENE-002 + P2-AUTH-003 EXPAND-phase SQL landed
  P2-AUTH-001-B + P2-AUTH-003 EXPAND-phase SQL landed in 133ae48
  but the task itself is blocked on the credential incident).

Blocked:
- P2-AUTH-003 (Phase 2 schema task): blocked on a credential
  exposure incident that occurred during the in-session dynamic
  verify attempt. The migration SQL file is safe (no secret
  content) and committed; the dynamic MySQL apply + checksum +
  orphan + shadow-read + rollback evidence is deferred to a
  future session after the operator rotates the affected
  secrets and recreates the MySQL volume. See WORKLOG
  "2026-07-27 (security incident)" for the full non-sensitive
  record.

Current Work:
- Session cleanup after the credential exposure incident is
  complete: WORKLOG and TASKS record the incident without any
  secret values; P2-AUTH-003 is `blocked` (not `done`); no further
  secret scan, history rewrite, or migration auto-verify is
  performed in this session.
- P2-AUTH-001-A and P2-AUTH-001-B remain done; their commits
  (da6f598, 9b4aaf9) carry no secret values.
- P2-AUTH-001-E is still blocked on the cross-cutting port
  promotion batch (P2-DISC-001/002/003) per
  ADR-MIG-CROSS-CUTTING-PORTS-PROMOTION.

Last Validation:
- ./mvnw verify -B full backend-spring reactor: PASS (49.8 s,
  1795 tests, 0 failures, 4 skipped) — taken before the
  credential incident; the verify signal still describes the
  state of the codebase at the last good point.

Next (after operator clears the credential incident and authorises
the verify cycle):
1. Re-verify P2-AUTH-003 dynamic evidence (Flyway apply, row-
   count checksum, orphan SELECT, shadow-read, IF EXISTS rollback)
   against a freshly-rotated .env and a recreated MySQL volume.
2. Resume P2-AUTH-001-E (RBAC/permission ownership) once
   P2-DISC-001/002/003 promotion batch lands.
3. Continue P2-AUTH-001-C, D, F, G as their dependencies clear.
4. P2-AUTH-002 / P2-AUTH-004 / P2-RBAC-001 / P2-GATE.
5. Phase 2 gate validation.

Operator actions required (NOT performed by the agent):
- Rotate MySQL user password, Redis password, Nacos admin
  password, JWT signing key, GitHub / Google OAuth client
  secrets, SMTP server password in their respective authorised
  secret stores.
- Drop the named volume `ulticode_mysql_data` and recreate the
  MySQL container against the rotated env.
- Restart all backend-* JVMs so they reload the rotated env.
- Notify the OAuth / SMTP providers of the secret rotation
  requirement.
- Force-rewrite git history and force-push only if the operator
  confirms a scrub is necessary; the current commit history
  carries no secret values (verified by `.env` in `.gitignore`
  and no matches in any tracked file), so this step is only
  required if the operator judges the prior session messages
  expose material that must not propagate.

Dirty Worktree:
No — `git status` clean. Local ahead of origin/main: 116 commits.
No push performed in this session.

PUSH: NOT pushed. Per AGENTS.md GitHub Write Gate, push requires
explicit user approval.

Key references:
- backend-spring/docs/MICROSERVICE_MIGRATION_GUIDE.md
- backend-spring/docs/migration-execution/TASKS.yaml
- backend-spring/docs/migration-execution/COVERAGE.md
- backend-spring/docs/migration-execution/DECISIONS.md
- backend-spring/docs/migration-execution/WORKLOG.md
- backend-spring/docs/migration-execution/_tools/update_task.py
- scripts/dev/dubbo-nacos-smoke.sh
- init-db/migrations/V20260727021915__Add_Authz_Version_And_Refresh_Token_Family.sql
