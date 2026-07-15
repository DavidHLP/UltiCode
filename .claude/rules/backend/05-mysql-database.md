---
paths:
  - "backend-spring/src/main/java/**/*Mapper.java"
  - "backend-spring/src/main/java/**/mapper/**/*.java"
  - "backend-spring/src/main/java/**/entity/**/*.java"
  - "backend-spring/src/main/java/**/service/**/*.java"
---

# MyBatis and persistence rules

- Follow the backend guide's annotation-based mapper style; do not add XML mappers or a second persistence abstraction.
- SQL values **MUST** use bound parameters such as `#{value}`. `${value}` is forbidden for request-controlled input and allowed for identifiers only after a closed whitelist.
- Queries **MUST** select explicit columns. Do not use `SELECT *`, rely on accidental column order, or return persistence entities as API contracts.
- Every query **MUST** define its cardinality. A mapper expected to return one row must have a database or predicate guarantee that prevents ambiguous results.
- Pagination **MUST** use deterministic ordering with a stable tie-breaker. Avoid unbounded reads and large in-memory filtering.
- Do not issue mapper calls inside loops when one set-based or batch query can express the operation.
- Write operations **MUST** check affected-row counts when absence, stale state, or a concurrent update changes the outcome.
- Transaction boundaries belong to the service operation that owns the invariant; do not add transactions to controllers or mappers.
- Do not hold database transactions open across remote calls, blocking waits, sandbox execution, or message delivery.
- Dynamic predicates **MUST** preserve authorization, tenancy/ownership, and soft-delete filters from the established query path.
- New indexes or constraints require a canonical migration and a query-based reason; annotations are not schema migration tools.
