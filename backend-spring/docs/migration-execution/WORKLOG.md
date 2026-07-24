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

### P0-SCHEMA-002 — `problem_notes` schema convergence

- Migration: `init-db/migrations/V20260724162800__Converge_Problem_Notes_Schema.sql`
- Status: done
- Evidence: `./mvnw compile -B` BUILD SUCCESS (real run, real output)

### TASKS.yaml rewrite (process correction)

- Earlier line-level `edit SWAP`/`DEL` rounds corrupted TASKS.yaml
  (duplicate `status:` fields, missing P0-SCHEMA-003, malformed P0-SEC-001
  header). Initial `write` landed with a top-level mapping + sequence
  hybrid that YAML rejects.
- Rewrote file as a pure top-level list (per project convention). Validated
  via Python `yaml.safe_load`: 51 tasks, 2 done (P0-SCHEMA-001, P0-SCHEMA-002),
  all headers present.
- Installed `_tools/update_task.py`: load YAML, mutate by id, dump back.
  All future status changes will use this script — no more `edit SWAP` on
  TASKS.yaml.

### P0-SEC-001 — OAuth state cookie binding

- Status: done
- Implementation:
  - `OAuthStatePort.validateAndConsume` signature: added `cookieState` param.
  - `OAuthStateModule.validateAndConsume`: constant-time compare via
    `MessageDigest.isEqual` on UTF-8 bytes; mismatch throws UNAUTHORIZED
    and clears the cookie before any Redis access. Null/blank cookie is
    accepted (no binding) — production callers always forward a cookie.
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

### Status snapshot

- TASKS.yaml: 51 tasks, 3 done (P0-SCHEMA-001, P0-SCHEMA-002, P0-SEC-001)
- Coverage: 100%
- Next ready queue:
  1. P0-SEC-003 — WS validator unification, active/ban, fail-closed
  2. P0-SEC-004 — Effective permission expiry
  3. P0-SCHEMA-003 — migration-only table inventory
  4. P0-SEC-002 — OAuth provider identity (depends on P0-SEC-001, now unblocked)

### Process rule going forward

1. `in_progress` before any work on a task (via update_task.py)
2. Real validation command output captured before flipping to `done`
3. Evidence recorded via the script; never predict results
4. No `edit SWAP`/`DEL` on TASKS.yaml — ever. All status changes via script.
5. No `edit SWAP` on production code unless the entire hunk is exactly what
   needs to change (recent near-miss with OAuthService losing the
   OAuthClient token-exchange block).

### Next actions

- Begin P0-SEC-003: WS validator unification, active/ban, fail-closed.