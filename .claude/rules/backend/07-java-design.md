---
paths:
  - "backend-spring/src/main/**/*.java"
---

# Java design rules

- Prefer composition and small cohesive collaborators over inheritance. Introduce an interface only for a real boundary, multiple behavior variants, or an owned test seam.
- Do not add pass-through services, wrappers, or adapters that only rename another method without owning policy or translation.
- Keep domain decisions separate from I/O orchestration so the decision logic can be tested without Spring, the database, network, queue, or clock.
- Value objects **SHOULD** be immutable and validate their invariants at construction. Do not represent a multi-field invariant as unrelated primitives across layers.
- Retried or duplicate-prone operations **MUST** define idempotency behavior. Do not rely on a prior read followed by an unconditional write for uniqueness or single-use state.
- Shared mutable state **MUST** have an explicit concurrency owner. Avoid static mutable fields, unbounded executors/queues, and locks that cover I/O.
- Resource ownership **MUST** be visible: the creator closes the resource unless ownership is explicitly transferred.
- Caches **MUST** identify the source of truth, key namespace, expiry, invalidation, and stale-read tolerance before being introduced.
- External calls **MUST** define timeout, retry, and failure semantics. Retries need bounded attempts and must not duplicate non-idempotent effects.
- Optimize measured bottlenecks, not hypothetical ones. Performance shortcuts must preserve correctness and include evidence or a regression benchmark/test.
