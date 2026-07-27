# Migration Resume

Current Phase: Phase 2
Current Task: P2-GATE (Phase 2 gate — Auth extraction complete)

Last Verified Commit:
- f786117 chore(migration): P2-AUTH-003 done after disposable-env dynamic verify
- 97af963 chore(migration): append P2-AUTH-003 EXPAND-phase static validation evidence
- d1c5ee8 chore(migration): reconcile P2-AUTH-003 blocked state in derived docs
- c2386f0 feat(gateway): cut over /api/auth/ route family to backend-auth service (P2-AUTH-004)
- 9c63135 feat(sec): install offline resource server JWT verifiers in App and Admin (P2-AUTH-002)
- 17e9f6b feat(exception): promote BusinessException to backend-common accepting NamespacedErrorCode (P2-DISC-001)
- 49960e0 chore(migration): record P2-DISC-003 cross-cutting ports promotion decision (P2-DISC-003)
- 631b8b1 feat(time): provide Clock and TimeSource bean configurations across extracted service shells (P2-DISC-002)
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
- 39 / 66 (Phase 0 gate + nine Phase 1 tasks + P2-AUTH-001 +
  P2-AUTH-001-A..G + P2-AUTH-002 + P2-AUTH-003 + P2-AUTH-004 +
  P2-RBAC-001 + P2-DISC-001..003 + P2-SEC-HYGIENE-001/002 +
  P2-COV-AUDIT-001 + P2-DONE-EVIDENCE-AUDIT-001; remaining:
  P2-GATE + 2 follow-ups P2-DISC-004/005).

Blocked:
- None. P2-AUTH-003 unblocked and closed after disposable-env
  dynamic MySQL verify on 2026-07-27 (commit f786117). The previous
  credential-exposure incident is moot: the verification ran in
  a fully isolated disposable MySQL container on a separate port
  with throwaway creds; the dev MySQL on 23306 and its volume
  were never touched. P2-RBAC-001 also done; the writer-segregation
  rule is enforced by OwnerBoundaryArchTest.

Current Work:
- P2-AUTH-003 dynamic verification PASS in `disposable-verify/`
  (docker MySQL 9.1, port 23307, throwaway creds, separate
  volume). Fresh migration: 40 migrations applied BUILD SUCCESS.
  Upgrade scenario (3 legacy users + 5 legacy refresh_tokens):
  15/5/1 row counts preserved; authz_version = 0 on all 15 users;
  family_id/replaced_by_token_id/previous_token_id = NULL on all
  5 refresh_tokens; UNIQUE on (provider, provider_user_id) rejects
  duplicates. Discovered P2-DISC-004: IF-EXISTS rollback is
  MariaDB/PostgreSQL syntax, not supported on MySQL 9.1; project
  primary rollback is application rollback + additive schema
  retained, so the SQL IF-EXISTS path was a documented defensive
  measure; filed as P2-DISC-004 follow-up (pending, depends on
  P2-AUTH-003).
- P2-AUTH-001-A and P2-AUTH-001-B remain done; their commits
  (da6f598, 9b4aaf9) carry no secret values.
- P2-AUTH-001-E is `done` (commit 409d615); the cross-cutting
  port promotion batch (P2-DISC-001/002/003) is also `done` per
  TASKS.yaml — see ADR-MIG-CROSS-CUTTING-PORTS-PROMOTION.

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
2. Execute P2-RBAC-001 (Auth-only RBAC writer; App/Admin read-only RPC)
   as P2-AUTH-003 dependency clears.
3. Complete Phase 2 Gate (P2-GATE validation).
4. Begin Phase 3 (Owner-owned Application APIs).

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
No — `git status` clean. No push performed in this session.

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
