# Migration Resume

Current Phase: Phase 1
Current Task: P1-INFRA-004 (in_progress)

Last Verified Commit:
c93aad9 chore(migration): record P1-INFRA-002 commit hash

Completed:
13 / 51 (Phase 0 gate, P1-INFRA-001, P1-INFRA-002)

Blocked:
(none)

Current Work:
P1-INFRA-004 inventories Gateway route families and enforces removal of
client-supplied identity/role/service headers while retaining Legacy routing.

Last Validation:
./mvnw -pl backend-common,backend-legacy -am test -B
PASS — backend-common 62 tests; backend-legacy existing unit suite green.

Next:
1. Complete P1-INFRA-004 Gateway baseline
2. P1-API-001 — provider-owned API submodules
3. P1-INFRA-003 — Dubbo Triple/Nacos registry wiring

Dirty Worktree:
Yes — P1-INFRA-004 is in progress. Pre-existing untracked
backend-spring/docs/MICROSERVICE_MIGRATION_GUIDE.md remains outside task diffs.

PUSH: NOT PUSHED. GitHub writes require explicit user approval.

Key references:
- backend-spring/docs/MICROSERVICE_MIGRATION_GUIDE.md
- backend-spring/docs/migration-execution/TASKS.yaml
- backend-spring/docs/migration-execution/COVERAGE.md
- backend-spring/docs/migration-execution/DECISIONS.md
- backend-spring/docs/migration-execution/WORKLOG.md
- backend-spring/docs/migration-execution/_tools/update_task.py

[backend-spring/docs/migration-execution/WORKLOG.md#9EE2]
INS.TAIL:

### P1-INFRA-001 — Maven reactor conversion

- Status: done; local commit pending.
- Root `pom.xml` is now a `packaging=pom` reactor with `backend-common`,
  `backend-api`, `backend-auth`, `backend-admin`, `backend-app`, and
  `backend-legacy`.
- Existing monolith source/resources/tests and ArchUnit freeze store moved
  unchanged into `backend-legacy`.
- Dockerfile, `backend-spring/start.cjs`, and `ecosystem.config.cjs` now select
  `backend-legacy`.
- Review fixes: reactor-wide `target/` ignore and CI surefire artifact glob.
- `./mvnw -pl backend-legacy verify -B`: PASS, 1804 tests.
- `./mvnw verify -B`: PASS across 7 reactor projects, 1804 tests.
- Standards review PASS; Spec findings closed or explicitly assigned to
  dependent task P1-API-001.