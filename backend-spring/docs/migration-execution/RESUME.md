# Migration Resume

Current Phase: Phase 1
Current Task: P1-INFRA-003 (blocked; P1-INFRA-003-DISC filed)

Last Verified Commit:
c8246b3 chore(migration): mark P1-INFRA-003 blocked; file P1-INFRA-003-DISC

Completed:
15 / 51 (Phase 0 gate plus four Phase 1 tasks) + P1-INFRA-003 wiring (blocked)

Blocked:
P1-INFRA-003 (Dubbo → Nacos runtime registration not provable in sandbox)

Current Work:
- P1-INFRA-001, P1-INFRA-002, P1-INFRA-004, P1-API-001 are done.
- P1-INFRA-003 wiring is complete: dubbo-spring-boot-starter 3.3.6 +
  dubbo-registry-nacos 3.3.6 + nacos-client 2.5.1 transitive; Triple
  protocol on port -1; Nacos dev-namespace registry wired with
  use-as-config-center=false / use-as-metadata-center=false; @DubboService
  placeholder HealthCheckService in com.ulticode.dubbo.provider triggers
  the export path. Spring Boot 3.2.5 + Dubbo 3.3.6 bootstrap completes
  in 4.5 s; Dubbo logs the service-discovery-registry URL.
  scripts/dev/dubbo-nacos-smoke.sh brings up MySQL+Redis+Nacos, runs
  Flyway, starts backend-legacy, and polls the Nacos instance list.
  The sandbox cannot observe the actual HTTP putInstance in time; the
  next agent must rerun the smoke on a real Linux dev host to close
  the gap (P1-INFRA-003-DISC).
- Do NOT move to P1-OBS-001 or P1-INFRA-005 until P1-INFRA-003-DISC is
  resolved: trace propagation and the per-service shells both depend
  on the registration contract being live.

Last Validation:
./mvnw -pl backend-legacy -am -Dtest=DubboBootstrapConfigTest
-Dsurefire.failIfNoSpecifiedTests=false test -B
PASS — 3 / 3 (application name / triple protocol / nacos dev namespace
all bind from application-dubbo-smoke-test.yml).

Next:
1. Resolve P1-INFRA-003-DISC (smoke on a real host, or via Phase 4 P4-RPC-001)
2. P1-OBS-001 — HTTP/Dubbo trace propagation
3. P1-INFRA-005 — independent service starter shells
4. P1-GATE — Phase 1 gate (all of P1-INFRA-001..005 + P1-OBS-001)

Dirty Worktree:
Clean (last commit c8246b3; pre-existing untracked
backend-spring/docs/MICROSERVICE_MIGRATION_GUIDE.md remains outside
task diffs).

PUSH: NOT pushed. GitHub writes require explicit user approval.

Key references:
- backend-spring/docs/MICROSERVICE_MIGRATION_GUIDE.md
- backend-spring/docs/migration-execution/TASKS.yaml
- backend-spring/docs/migration-execution/COVERAGE.md
- backend-spring/docs/migration-execution/DECISIONS.md
- backend-spring/docs/migration-execution/WORKLOG.md
- backend-spring/docs/migration-execution/_tools/update_task.py
- scripts/dev/dubbo-nacos-smoke.sh
