# Migration Worklog

Append-only log of significant events. NOT a task state source of truth
(see TASKS.yaml).

## 2026-07-25

### Initial scaffold

- Read MICROSERVICE_MIGRATION_GUIDE.md fully (947 lines, 0-947).
- Read backend-spring/AGENTS.md and backend-spring/pom.xml baseline.
- Inspected current repo state (backups drift, problem_notes drift, OAuth
  state binding gap, WS fail-open paths, users email non-unique).
- Created persistence files under `backend-spring/docs/migration-execution/`:
  - `TASKS.yaml` — full Phase 0–7 task DAG.
  - `COVERAGE.md` — guide → task mapping + checklist mapping + risk mapping.
  - `RESUME.md` — recovery snapshot.
  - `DECISIONS.md` — initial ADR set.
  - `WORKLOG.md` — this file.

### P0-SCHEMA-001 — `backups` canonical migration

- Migration: `init-db/migrations/V20260724162738__Create_Backups_Table.sql`
- Status: done
- Evidence: `./mvnw compile -B` BUILD SUCCESS (real run, real output)
- Commit: 3f1c61fd16f26a5686228e3f87ef7aac01bba462 (feat(db))

### P0-SCHEMA-002 — `problem_notes` schema convergence

- Migration: `init-db/migrations/V20260724162800__Converge_Problem_Notes_Schema.sql`
- Status: done
- Evidence: `./mvnw compile -B` BUILD SUCCESS (real run, real output)
- Commit: 3f1c61fd16f26a5686228e3f87ef7aac01bba462 (feat(db), shared with P0-SCHEMA-001)

### TASKS.yaml rewrite (process correction)

- Earlier line-level `edit SWAP`/`DEL` rounds corrupted TASKS.yaml
  (duplicate `status:` fields, missing P0-SCHEMA-003, malformed P0-SEC-001
  header). Initial `write` landed with a top-level mapping + sequence
  hybrid that YAML rejects.
- Rewrote file as a pure top-level list. Validated via Python
  `yaml.safe_load`: 51 tasks, all headers present.
- Installed `_tools/update_task.py`: load YAML, mutate by id, dump back.
  All future status changes use this script — no more `edit SWAP` on
  TASKS.yaml.
- Commit: 9172541ec9bfef35fb7db916608ab6340f2b9d57 (chore(migration))

### P0-SEC-001 — OAuth state cookie binding

- Status: done
- Implementation:
  - `OAuthStatePort.validateAndConsume` signature: added `cookieState` param.
  - `OAuthStateModule.validateAndConsume`: constant-time compare via
    `MessageDigest.isEqual` on UTF-8 bytes; mismatch throws UNAUTHORIZED
    and clears the cookie before any Redis access.
  - `OAuthService.handleGithubCallback`/`handleGoogleCallback`: 4-arg
    signatures forwarding cookieState.
  - `AuthController` callback handlers: extract `oauth_state_<provider>`
    HttpOnly cookie from `HttpServletRequest`, forward to OAuthService.
  - Tests:
    - `OAuthStateModuleTest`: added 3 new tests (cookie mismatch,
      blank cookie, null cookie). All 11 module tests pass.
    - `OAuthServiceTest`: updated all 8 callback/verify sites to 4-arg.
      All 11 service tests pass.
- Evidence:
  - `./mvnw test -B` → Tests run: 1791, Failures: 0, Errors: 0, Skipped: 4,
    BUILD SUCCESS at 2026-07-25T00:39:16+08:00.
- Commit: 90c6a0965838aec1e7b14fcad29870b902489080 (fix(security))

### Hash recording

- Commit: 65cc4af6b chore(migration): record phase 0 commit hashes in TASKS.yaml
- PUSH: NOT pushed. Per GitHub Write Gate, push requires explicit user
  approval.

### Status snapshot

- TASKS.yaml: 51 tasks, 3 done (P0-SCHEMA-001, P0-SCHEMA-002, P0-SEC-001)
- Local commits: 4 (all atomic, by task or task group)
- Coverage: 100%
- Working tree: clean

### Process rules (sticky going forward)

1. `in_progress` before any work on a task (via update_task.py)
2. Real validation command output captured before flipping to `done`
3. Evidence recorded via the script; never predict results
4. No `edit SWAP`/`DEL` on TASKS.yaml — ever. All status changes via script.
5. Commit + record hash in TASKS.yaml `commits:` field at the end of each
   task or tight task group. Don't accumulate > 1 task uncommitted.
6. No `git push` without explicit user approval (GitHub Write Gate).

### Next actions

- Begin P0-SEC-003: WS validator unification, active/ban, fail-closed.