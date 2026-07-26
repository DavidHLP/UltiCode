# Migration Resume

Current Phase: Phase 2
Current Task: P2-AUTH-001 (refresh-token rotation extraction to backend-auth)

Last Verified Commit:
5174e98 chore(migration): record P1-INFRA-003 and P1-GATE commit hashes

Completed:
18 / 51 (Phase 0 gate + seven Phase 1 tasks done; P1-GATE closed)

Blocked:
None

Current Work:
- Phase 1 is fully closed. All P1-INFRA-* tasks, P1-API-001, P1-OBS-001,
  P1-INFRA-003-DISC, and P1-GATE are done/superseded.
- P2-AUTH-001 in progress. Setup complete: backend-auth pom dependencies and
  application.yml added (datasource, Redis, Flyway, JWT, mail, MyBatis-Plus,
  Security, Dubbo/Nacos). backend-auth compiles.
- Next sub-task: copy auth entities/mappers from legacy, then move
  AuthController/Service/Security code and add tests.

Last Validation:
- `./mvnw verify -B` across backend-spring reactor: PASS (40.6 s).
- `scripts/dev/dubbo-nacos-smoke.sh`: PASS (ulticode-backend-legacy registered
  in Nacos dev namespace with one healthy instance).

Next:
1. P2-AUTH-001: extract refresh-token rotation into backend-auth.
2. P2-AUTH-002: resource-server JWT verifier per service.
3. P2-AUTH-003: provider identity / authz version.
4. P2-AUTH-004: Gateway `/auth/**` cutover.
5. P2-RBAC-001: auth-only RBAC writer.
6. P2-GATE.

Dirty Worktree:
Yes — Phase 1 close-out files + P1-INFRA-003 fix files not yet committed.

PUSH: NOT pushed. GitHub writes require explicit user approval.

Key references:
- backend-spring/docs/MICROSERVICE_MIGRATION_GUIDE.md
- backend-spring/docs/migration-execution/TASKS.yaml
- backend-spring/docs/migration-execution/COVERAGE.md
- backend-spring/docs/migration-execution/DECISIONS.md
- backend-spring/docs/migration-execution/WORKLOG.md
- backend-spring/docs/migration-execution/_tools/update_task.py
- scripts/dev/dubbo-nacos-smoke.sh
