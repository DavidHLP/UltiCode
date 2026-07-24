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
    `banned_until` accounts.
  - `JwtChannelInterceptor.validateUserSession`: was log-and-return;
    now throws `WEBSOCKET_SESSION_MISSING` so SEND/SUBSCRIBE fail
    closed.
- Tests: 8 -> 12 in DefaultWebSocketAuthenticator; 9 -> 10 in
  JwtChannelInterceptor.
- Evidence: 1797 tests, BUILD SUCCESS 2026-07-25T00:47:35+08:00.
- Commit: 626e665a4755e0845072c2bd9d89f0953962dd86 (fix(security))
- Hash recorded: 62a2399 (chore(migration))

### WORKLOG/RESUME update after P0-SEC-003

- TokenBlacklistPort design note: port deliberately read-only; runtime
  revocation lives in RefreshTokenService. Phase 0 should NOT widen
  the read port.
- Commit: d7a04be5e (chore(migration))

### P0-SEC-004 — Effective permission expiry filter for /auth/permissions

- Status: done
- Implementation:
  - `PermissionServiceImpl.getUserPermissions`: LambdaQueryWrapper
    predicate `(expires_at IS NULL OR expires_at > NOW(clock))`. Null
    = permanent; future = valid; past = filtered.
  - The predicate lives at the SQL layer (not a Java post-filter), so
    the DB does the work and the service stays declarative.
- Tests:
  - `PermissionServiceTest`: 14 -> 15 (+1 filtersExpiredPermissions).
  - Removed a brittle wrapper-inspection test (relied on MyBatis-Plus
    lambda cache being initialized outside a running session).
- Documented semantics:
  - /auth/permissions is role-based via the JWT 'role' claim.
  - user_permissions layer is advisory: this filter is the data-honesty
    fix; full enforcement via GrantedAuthority / PermissionEvaluator
    is a Phase 2/3 per-endpoint opt-in.
- Evidence: 1798 tests, BUILD SUCCESS 2026-07-25T00:51:31+08:00.
- Commit: 0e9c3494773f235ba2f918f6993b7cb8f766b212 (fix(security))
- Hash recorded: dbdb04e (chore(migration))

### Status snapshot

- TASKS.yaml: 51 tasks, 5 done
  (P0-SCHEMA-001, P0-SCHEMA-002, P0-SEC-001, P0-SEC-003, P0-SEC-004)
- Local commits: 10 (atomic per task or task group + hash recording)
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
7. Test files: prefer full `write` over `edit SWAP` when scope > 1 method.
8. When asserting on LambdaQueryWrapper at unit-test level, capture the
   wrapper and verify the SELECT was called; do NOT call getSqlSegment()
   outside a running MyBatis-Plus session (lambda cache NPE).

### Next actions

- P0-SCHEMA-003 — Inventory migration-only tables. Writes to DECISIONS.md
  (ADR-MIG-INV extension). No schema change.
- P0-SEC-002 — OAuth provider identity & verified-email binding
  (depends on P0-SEC-001, now unblocked).