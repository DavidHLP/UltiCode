# Migration Resume

Current Phase: Phase 2
Current Task: P2-AUTH-001 (refresh-token rotation extraction to backend-auth)

Last Verified Commit:
TBD (Phase 1 gate commit pending)

Completed:
18 / 51 (Phase 0 gate + seven Phase 1 tasks done; P1-GATE closed)

Blocked:
None

Current Work:
- Phase 1 is fully closed. All P1-INFRA-* tasks, P1-API-001, P1-OBS-001,
  P1-INFRA-003-DISC, and P1-GATE are done/superseded.
- P1-INFRA-003 final fix: okhttp 4.12.0 for Nacos client compatibility,
  merged duplicate `management:` / `logging:` blocks in
  `backend-legacy/src/main/resources/application.yml`, added
  `TracerHolder` bridge so `DubboTraceFilter` can be SPI-instantiated by
  Dubbo, and switched `scripts/dev/dubbo-nacos-smoke.sh` to Nacos JWT
  `accessToken` auth.
- P1-GATE validation: `./mvnw verify -B` PASS (full reactor, 0 failures).

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
