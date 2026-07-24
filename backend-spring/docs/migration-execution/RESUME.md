# Migration Resume

Current Phase: Phase 0
Current Task: P0-SEC-004 (about to start)

Last Verified Commit:
62a2399 chore(migration): record P0-SEC-003 commit hash in TASKS.yaml
(HEAD — local only, NOT pushed to origin)

Completed:
4 / 51
- P0-SCHEMA-001 (done, BUILD SUCCESS 2026-07-25T00:29:24+08:00)
  commit: 3f1c61fd16f26a5686228e3f87ef7aac01bba462 (feat(db))
- P0-SCHEMA-002 (done, BUILD SUCCESS 2026-07-25T00:29:24+08:00)
  commit: 3f1c61fd16f26a5686228e3f87ef7aac01bba462 (feat(db))
- P0-SEC-001 (done, 1791 tests pass 2026-07-25T00:39:16+08:00)
  commit: 90c6a0965838aec1e7b14fcad29870b902489080 (fix(security))
- P0-SEC-003 (done, 1797 tests pass 2026-07-25T00:47:35+08:00)
  commit: 626e665a4755e0845072c2bd9d89f0953962dd86 (fix(security))

Local commit chain (oldest first):
  9172541e chore(migration): phase 0-7 task DAG + persistence scaffold
  3f1c61fd feat(db): backups + problem_notes migrations
  90c6a096 fix(security): bind oauth state cookie
  65cc4af6 chore(migration): record P0-SCHEMA/SEC-001 hashes
  4a60c4aa chore(migration): update RESUME + WORKLOG with hashes
  626e665a fix(security): ws validator unification + active/ban + fail closed
  62a2399 chore(migration): record P0-SEC-003 hash

PUSH: NOT PUSHED. Per GitHub Write Gate, push requires explicit user
approval. Local commits only.

Blocked:
(none)

Current Work:
Implementing P0-SEC-004 — effective permission expiry filter for
`/auth/permissions`. Filter `user_permissions` whose `expires_at` is
in the past; document role-only semantics; test expired / future / null.

Last Validation:
- ./mvnw test -B → 1797 tests, 0 failures, 0 errors, 4 skipped
  BUILD SUCCESS at 2026-07-25T00:47:35+08:00 (commit 626e665a)

Next (ready queue):
1. P0-SEC-004 — Effective permission expiry (no deps)
2. P0-SCHEMA-003 — migration-only table inventory (no deps)
3. P0-SEC-002 — OAuth provider identity (depends on P0-SEC-001, unblocked)
4. P0-JUDGE-001 — judge outbox/fence/stream design (no deps)
5. P0-ARCH-001 — table owner manifest (no deps)
6. P0-ARCH-002 — ArchUnit baseline (depends on P0-ARCH-001)

Dirty Worktree:
No

Key references:
- backend-spring/docs/MICROSERVICE_MIGRATION_GUIDE.md
- backend-spring/docs/migration-execution/TASKS.yaml
- backend-spring/docs/migration-execution/COVERAGE.md
- backend-spring/docs/migration-execution/DECISIONS.md
- backend-spring/docs/migration-execution/WORKLOG.md
- backend-spring/docs/migration-execution/_tools/update_task.py