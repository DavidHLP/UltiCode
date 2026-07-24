# Migration Resume

Current Phase: Phase 0
Current Task: P0-SEC-003 (about to start)

Last Verified Commit:
65cc4af6b chore(migration): record phase 0 commit hashes in TASKS.yaml
(HEAD — local only, NOT pushed to origin)

Completed:
3 / 51
- P0-SCHEMA-001 (done, BUILD SUCCESS 2026-07-25T00:29:24+08:00)
  commit: 3f1c61fd16f26a5686228e3f87ef7aac01bba462 (feat(db))
- P0-SCHEMA-002 (done, BUILD SUCCESS 2026-07-25T00:29:24+08:00)
  commit: 3f1c61fd16f26a5686228e3f87ef7aac01bba462 (feat(db))
- P0-SEC-001 (done, 1791 tests pass 2026-07-25T00:39:16+08:00)
  commit: 90c6a0965838aec1e7b14fcad29870b902489080 (fix(security))

Local commit chain (oldest first):
  9172541ec9bfef35fb7db916608ab6340f2b9d57 chore(migration): docs scaffold
  3f1c61fd16f26a5686228e3f87ef7aac01bba462 feat(db): backups + problem_notes migrations
  90c6a0965838aec1e7b14fcad29870b902489080 fix(security): bind oauth state cookie
  65cc4af6b                                          chore(migration): record commit hashes

PUSH: NOT PUSHED. Per GitHub Write Gate, push requires explicit user
approval. Local commits only.

Blocked:
(none)

Current Work:
Implementing P0-SEC-003 — unify HTTP/WS JWT validator and add
active/ban checks to WS CONNECT, plus fail-closed SEND/SUBSCRIBE.

Last Validation:
- ./mvnw test -B  → 1791 tests, 0 failures, 0 errors, BUILD SUCCESS
  at 2026-07-25T00:39:16+08:00 (in commit 90c6a0965)

Next (ready queue):
1. P0-SEC-003 — WS validator unification (no deps)
2. P0-SEC-004 — Effective permission expiry (no deps)
3. P0-SCHEMA-003 — migration-only table inventory (no deps)
4. P0-SEC-002 — OAuth provider identity (depends on P0-SEC-001, now unblocked)

Dirty Worktree:
No (clean; MICROSERVICE_MIGRATION_GUIDE.md is pre-existing untracked)

Key references:
- backend-spring/docs/MICROSERVICE_MIGRATION_GUIDE.md
- backend-spring/docs/migration-execution/TASKS.yaml
- backend-spring/docs/migration-execution/COVERAGE.md
- backend-spring/docs/migration-execution/DECISIONS.md
- backend-spring/docs/migration-execution/WORKLOG.md
- backend-spring/docs/migration-execution/_tools/update_task.py