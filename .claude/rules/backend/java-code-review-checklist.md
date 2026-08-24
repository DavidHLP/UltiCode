---
paths:
  - "services/**/src/**/*.java"
  - "{backend-auth,backend-admin,backend-app}/src/**/*.java"
  - "docker/sandbox/harness/java/src/**/*.java"
kind: rules
summary: 'Java code review checklist for Spring Boot.'
---

# Java review workflow

Before completing a Java change:

1. Build the changed call/data-flow map and list affected contracts, configuration, persistence, and tests.
2. Apply `java-programming.md`, `java-exception-logging.md`, `java-unit-testing.md`, `java-security.md`, `mysql-database.md`, `java-project-structure.md`, and `java-design.md`; apply `java-runtime-diagnostics.md` only when runtime diagnostics were used.
3. Apply root and nested `AGENTS.md` plus `backend/spring-boot.md` for project/framework constraints.
4. Inspect success, failure, cleanup, cancellation, retry, and concurrent execution paths that the change can reach.
5. Confirm the selected unit/integration tests prove the regression and that the task diff contains no unrelated churn.
6. Report each remaining issue with location, consequence, and the smallest safe fix; do not merely repeat a checklist item.
