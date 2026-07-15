---
paths:
  - "backend-spring/src/**/*.{java,yml,yaml,properties}"
---

# Spring-specific completion review

In addition to `08-java-code-review-checklist.md`, verify:

- Spring annotations are effective through proxy invocation; no self-invoked transaction, async, cache, or method-security behavior is assumed.
- Bean scopes are thread-safe, dependencies are acyclic, and new configuration binds and validates at startup.
- Transaction propagation, rollback behavior, after-commit work, and remote I/O placement match the owned invariant.
- Scheduled/async work is bounded, idempotent where needed, observable on failure, and safe across restart or multiple instances.
- Controller validation, security annotations, response envelopes, and global exception translation remain consistent.
- Test slices or application-context tests cover wiring that a plain unit test cannot prove.
