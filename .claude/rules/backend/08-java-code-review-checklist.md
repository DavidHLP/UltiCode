---
paths:
  - "backend-spring/src/**/*.java"
  - "docker/sandbox/harness/java/src/**/*.java"
---

# Java review workflow

Before completing a Java change:

1. Build the changed call/data-flow map and list affected contracts, configuration, persistence, and tests.
2. Apply `01` through `07` to the changed lines; apply `09` only when runtime diagnostics were used.
3. Apply root and nested `AGENTS.md` plus `springboot-rules.md` for project/framework constraints.
4. Inspect success, failure, cleanup, cancellation, retry, and concurrent execution paths that the change can reach.
5. Confirm the selected unit/integration tests prove the regression and that the task diff contains no unrelated churn.
6. Report each remaining issue with location, consequence, and the smallest safe fix; do not merely repeat a checklist item.
