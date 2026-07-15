---
paths:
  - "backend-spring/src/test/**/*.java"
  - "docker/sandbox/harness/java/src/test/**/*.java"
---

# Java test rules

- Tests **MUST** use JUnit 5 and describe observable behavior or a regression, not private implementation steps.
- Each test **MUST** arrange its own state and be deterministic, order-independent, and safe to run in parallel unless explicitly isolated.
- Do not use real network services, wall-clock timing, random unseeded data, or arbitrary `Thread.sleep()` in unit tests.
- Prefer controlled clocks, latches, futures, or the project's existing async test utilities for time and concurrency behavior.
- Mock external boundaries and expensive collaborators, not the class under test or simple value objects. Prefer state/result assertions over interaction-only tests.
- Mockito stubs and verifications **SHOULD** use precise arguments; broad `any()` matchers must not conceal an important contract.
- Assert the failure type and meaningful error code/state for negative paths. Security and authorization changes require denied-path tests.
- Race-sensitive behavior requires a test that coordinates competing operations rather than invoking them sequentially.
- When constructor dependencies change, update every affected fixture explicitly; do not weaken initialization just to make tests compile.
- Unit tests use the repository's normal test selection. Database, security, queue, sandbox, or cross-module behavior belongs in an explicitly selected `*IT.java` test when unit isolation cannot prove it.
- Tests **MUST NOT** depend on developer credentials, production-like secrets, or mutable data outside the test lifecycle.
