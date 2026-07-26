# Migration Resume

Current Phase: Phase 2
Current Task: P2-AUTH-001 (refresh-token rotation extraction to backend-auth)

Last Verified Commit:
- e50c84b chore(migration): log Phase 2 setup and P2-AUTH-001-B delegation

Completed:
- 18 / 51 (Phase 0 gate + seven Phase 1 tasks done; P1-GATE closed)

Blocked:
None

Current Work:
- Phase 1 is fully closed. All P1-INFRA-* tasks, P1-API-001, P1-OBS-001,
  P1-INFRA-003-DISC, and P1-GATE are done/superseded.
- P2-AUTH-001 in progress. Runtime setup complete:
  - backend-auth pom dependencies (Spring Security, validation, Redis, mail,
    MyBatis-Plus, MySQL, Redisson, JJWT) added.
  - backend-auth application.yml with datasource, Redis/Redisson, Flyway, JWT,
    mail, MyBatis-Plus, management/tracing, Dubbo/Nacos settings.
  - H2 test-scope dependency and backend-auth test application.yml added;
    disabled Redis/Redisson/Security/ManagementWebSecurity autoconfig and mail
    health indicator for context-load tests.
  - `./mvnw -pl backend-auth -am -B verify` PASS.
  - Full reactor `./mvnw verify -B` PASS.
- Scout inventory complete: all auth/security/refresh/permission/OAuth classes in
  backend-legacy mapped and an extraction order produced.
- Next sub-task: move refresh-token ownership into backend-auth as the first
  self-contained auth subdomain (RefreshToken entity, mapper, service, table).

Last Validation:
- `./mvnw verify -B` full backend-spring reactor: PASS (39.1 s, all modules).

Next:
1. P2-AUTH-001-A: move refresh-token ownership (entity/mapper/service/table)
   into backend-auth with tests.
2. P2-AUTH-001-B: move JWT/security plumbing into backend-auth.
3. P2-AUTH-001-C: move AuthController + session/account adapters.
4. P2-AUTH-002: resource-server JWT verifier per service.
5. P2-AUTH-003: provider identity / authz version additive migration.
6. P2-AUTH-004: Gateway `/auth/**` cutover.
7. P2-RBAC-001: auth-only RBAC writer.
8. P2-GATE.

Dirty Worktree:
Yes — backend-auth pom/test-resources changes + TASKS.yaml/WORKLOG.md updates
not yet committed.

PUSH: NOT pushed. GitHub writes require explicit user approval.

Key references:
- backend-spring/docs/MICROSERVICE_MIGRATION_GUIDE.md
- backend-spring/docs/migration-execution/TASKS.yaml
- backend-spring/docs/migration-execution/COVERAGE.md
- backend-spring/docs/migration-execution/DECISIONS.md
- backend-spring/docs/migration-execution/WORKLOG.md
- backend-spring/docs/migration-execution/_tools/update_task.py
- scripts/dev/dubbo-nacos-smoke.sh
