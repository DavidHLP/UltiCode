# Migration Worklog

Append-only log of significant events. NOT a task state source of truth
(see TASKS.yaml).

## 2026-07-25

### Initial scaffold

- Read MICROSERVICE_MIGRATION_GUIDE.md fully (947 lines, 0-947).
- Read backend-spring/AGENTS.md and backend-spring/pom.xml baseline.
- Inspected current repo state (backups drift, problem_notes drift, OAuth
  state binding gap, WS fail-open paths, users email non-unique).
- Created persistence files under `backend-spring/docs/migration-execution/`.

### P0-SCHEMA-001 — `backups` canonical migration

- Migration: `init-db/migrations/V20260724162738__Create_Backups_Table.sql`
- Status: done
- Commit: 3f1c61fd16f26a5686228e3f87ef7aac01bba462 (feat(db))

### P0-SCHEMA-002 — `problem_notes` schema convergence

- Migration: `init-db/migrations/V20260724162800__Converge_Problem_Notes_Schema.sql`
- Status: done
- Commit: 3f1c61fd16f26a5686228e3f87ef7aac01bba462 (feat(db), shared)

### TASKS.yaml rewrite (process correction)

- Line-level edits corrupted the file (duplicate status fields, missing
  P0-SCHEMA-003). Rewrote as pure top-level list, validated via
  `yaml.safe_load`. Installed `_tools/update_task.py`.
- Commit: 9172541ec9bfef35fb7db916608ab6340f2b9d57 (chore(migration))

### P0-SEC-001 — OAuth state cookie binding

- Status: done
- Implementation: `OAuthStateModule` constant-time compare via
  `MessageDigest.isEqual` before Redis consume; `OAuthStatePort` +
  `OAuthService` + `AuthController` signatures updated; 11+11 tests pass.
- Evidence: 1791 tests, BUILD SUCCESS 2026-07-25T00:39:16+08:00
- Commit: 90c6a0965838aec1e7b14fcad29870b902489080 (fix(security))

### Hash recording for P0-SCHEMA-001/002 + P0-SEC-001

- Commit: 65cc4af6b (chore(migration))

### Commit checkpoint (per advisory)

- After P0-SEC-001 done with three tasks uncommitted, stopped and
  recorded 3 atomic commits before continuing. RESUME/WORKLOG update.
- Commit: 4a60c4aa6 (chore(migration))

### P0-SEC-003 — WS validator unification + active/ban + fail-closed

- Status: done
- Implementation:
  - ErrorCode: WEBSOCKET_USER_BANNED (150006),
    WEBSOCKET_SESSION_MISSING (150007).
  - `DefaultWebSocketAuthenticator`: constructor adds Clock;
    `isBannedOrInactive(user)` rejects inactive / banned / future
    `banned_until` accounts; past `banned_until` admits (Admin flip is
    the canonical path, expire is the backstop).
  - `JwtChannelInterceptor.validateUserSession`: was log-and-return;
    now throws `WEBSOCKET_SESSION_MISSING` on null attributes / null
    user / wrong user type so SEND/SUBSCRIBE fail closed.
- Tests:
  - `DefaultWebSocketAuthenticatorTest`: 8 -> 12 (+4 inactive, future
    banned, past banned, no-until banned).
  - `JwtChannelInterceptorTest`: 9 -> 10 (+2 fail-closed on
    SEND-without-attrs and SUBSCRIBE-without-user).
- Evidence:
  - `./mvnw test -B` → 1797 tests, 0 failures, 0 errors, 4 skipped,
    BUILD SUCCESS at 2026-07-25T00:47:35+08:00.
- Commit: 626e665a4755e0845072c2bd9d89f0953962dd86 (fix(security))
- Hash recorded: 62a2399 (chore(migration))

### TokenBlacklistPort design note (deferred write path)

- The guide §7.1 mentions "access-token blacklist has reading end but
  no complete write chain in source". Inspection of the WS
  `TokenBlacklistPort` shows the port is deliberately read-only: a
  port-adapter audit removed the unused `blacklistToken(...)` writers
  because runtime revocation is owned by `RefreshTokenService` (DB-backed
  hash-only store). The write chain for access-token revocation does not
  exist by design; an admin instant-revoke feature would add its own
  writer port per the port's Javadoc. This is the correct Phase 0
  decision: do NOT widen the read port. Document in DECISIONS.md at
  next ADR update.

### Status snapshot

- TASKS.yaml: 51 tasks, 4 done
  (P0-SCHEMA-001, P0-SCHEMA-002, P0-SEC-001, P0-SEC-003)
- Local commits: 7 (atomic per task or task group + hash recording)
- Coverage: 100%
- Working tree: clean (modulo pre-existing untracked guide)
- PUSH: NOT pushed. Per GitHub Write Gate, push requires explicit user
  approval.

### Process rules (sticky going forward)

1. `in_progress` before any work on a task (via update_task.py)
2. Real validation command output captured before flipping to `done`
3. Evidence recorded via the script; never predict results
4. No `edit SWAP`/`DEL` on TASKS.yaml — ever. All status changes via script.
5. Commit + record hash in TASKS.yaml `commits:` field at the end of each
   task or tight task group. Don't accumulate > 1 task uncommitted.
6. No `git push` without explicit user approval (GitHub Write Gate).
7. Test files: prefer full `write` over `edit SWAP` when scope > 1 method
   or method body changes; line edits leave orphans easily.

### Next actions

- P0-SEC-004 — Effective permission expiry filter for `/auth/permissions`
  (no deps, ready).