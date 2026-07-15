---
paths:
  - "backend-spring/src/**/*.java"
  - "docker/sandbox/harness/java/src/**/*.java"
---

# Java completion checklist

Before completing a Java change, verify all applicable items:

- The code compiles on Java 17 and introduces no raw types, unsafe suppressions, field injection, or hidden null contract.
- The change follows the owning module's boundary and updates every caller, implementation, mapping, and serialized contract affected.
- Success, validation failure, domain failure, dependency failure, and cleanup paths have intentional behavior.
- Authentication/authorization, sensitive logging, injection, path/URL handling, and fail-closed behavior were reviewed where relevant.
- Transaction scope, conditional updates, locks, retries, schedulers, and shared state cannot lose or duplicate work under concurrency.
- Queries are bounded and parameterized, mapper calls are not hidden in loops, and schema assumptions have a migration.
- Resources, subscriptions, futures, executors, temporary files, and listeners are released on failure and cancellation.
- Tests cover the changed behavior and important regression path at the correct unit/integration level.
- The task diff contains no unrelated formatting, dead code, stale comments, debug output, secrets, or documentation drift.
