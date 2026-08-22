---
name: backend-05-mysql-database
description: MyBatis-Plus mapper and entity rules for MySQL.
globs:
  - services/**/src/main/java/**/*Mapper.java
  - {backend-auth,backend-admin,backend-app}/src/main/java/**/*Mapper.java
  - services/**/src/main/java/**/mapper/**/*.java
  - {backend-auth,backend-admin,backend-app}/src/main/java/**/mapper/**/*.java
  - services/**/src/main/java/**/entity/**/*.java
  - {backend-auth,backend-admin,backend-app}/src/main/java/**/entity/**/*.java
  - services/**/src/main/java/**/service/**/*.java
  - {backend-auth,backend-admin,backend-app}/src/main/java/**/service/**/*.java
condition: ["(?i)MyBatis|persistence"]
interruptMode: never
alwaysApply: false
---

# MyBatis and persistence rules

- Follow the backend guide's annotation-based mapper style; do not add XML mappers or a second persistence abstraction.
- SQL values **MUST** use bound parameters such as `#{value}`. `${value}` is forbidden for request-controlled input and allowed for identifiers only after a closed whitelist.
- New queries **MUST** select explicit columns. When safely modifying a legacy `SELECT *`, replace it if the result mapping can be verified without unrelated churn. Never rely on accidental column order or return persistence entities as API contracts.
- Every query **MUST** define its cardinality. A mapper expected to return one row must have a database or predicate guarantee that prevents ambiguous results.
- New mapper return values and safely modified contracts **MUST** use a typed entity, projection, DTO, scalar, or deliberately typed collection. Do not introduce raw `Map`/`HashMap` for a stable schema; preserve a legacy map contract when its consumers are outside the task's safe migration scope.
- Pagination **MUST** use deterministic ordering with a stable tie-breaker. Avoid unbounded reads and large in-memory filtering.
- Do not issue mapper calls inside loops when one set-based or batch query can express the operation.
- Collection parameters for `IN` or batch DML **MUST** define empty-input behavior and an upper bound; chunk large inputs rather than generating unbounded SQL.
- Write operations **MUST** check affected-row counts when absence, stale state, or a concurrent update changes the outcome.
- Update methods **MUST** name the fields they own. Do not bind a broad request/POJO into a full-row update that can overwrite fields the operation did not intend to change.
- Transaction boundaries belong to the service operation that owns the invariant; do not add transactions to controllers or mappers.
- Do not hold database transactions open across remote calls, blocking waits, sandbox execution, or message delivery.
- Dynamic predicates **MUST** preserve authorization, tenancy/ownership, and soft-delete filters from the established query path.
- New indexes or constraints require a canonical migration and a query-based reason; annotations are not schema migration tools.
