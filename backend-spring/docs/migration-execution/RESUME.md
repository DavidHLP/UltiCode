# Migration Resume

Current Phase: Phase 0
Current Task: P0-SEC-003 (about to start)

Last Verified Commit:
(pending first commit — no commits yet, working tree only)

Completed:
3 / 51
- P0-SCHEMA-001 (done, BUILD SUCCESS 2026-07-25T00:29:24+08:00)
- P0-SCHEMA-002 (done, BUILD SUCCESS 2026-07-25T00:29:24+08:00)
- P0-SEC-001 (done, 1791 tests pass 2026-07-25T00:39:16+08:00)

Blocked:
(none)

Current Work:
Implementing P0-SEC-003 — unify HTTP/WS JWT validator and add
active/ban checks to WS CONNECT, plus fail-closed SEND/SUBSCRIBE.

Last Validation:
- ./mvnw test -B  → 1791 tests, 0 failures, 0 errors, BUILD SUCCESS
  at 2026-07-25T00:39:16+08:00

Next (ready queue):
1. P0-SEC-003 — WS validator unification (no deps)
2. P0-SEC-004 — Effective permission expiry (no deps)
3. P0-SCHEMA-003 — migration-only table inventory (no deps)
4. P0-SEC-002 — OAuth provider identity (depends on P0-SEC-001, now unblocked)

Dirty Worktree:
Yes — schema migrations + OAuth binding code, no commits yet

Key references:
- backend-spring/docs/MICROSERVICE_MIGRATION_GUIDE.md
- backend-spring/docs/migration-execution/TASKS.yaml
- backend-spring/docs/migration-execution/COVERAGE.md
- backend-spring/docs/migration-execution/DECISIONS.md
- backend-spring/docs/migration-execution/WORKLOG.md
- backend-spring/docs/migration-execution/_tools/update_task.py