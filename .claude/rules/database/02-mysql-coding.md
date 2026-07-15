---
paths:
  - "init-db/migrations/**/*.sql"
  - "backend-spring/src/main/java/**/*Mapper.java"
  - "backend-spring/src/main/java/**/mapper/**/*.java"
---

# MySQL rules

- SQL **MUST** be compatible with the MySQL version configured by Compose; do not assume behavior from another server or version.
- Schema changes **MUST** use a new canonical Flyway migration and follow the migration rule. Never hide schema mutation in application startup code.
- Tables and text columns **MUST** use `utf8mb4` with the established schema/table collation; do not introduce a conflicting collation casually.
- New table, column, index, and constraint identifiers **MUST** follow the project's lowercase naming convention and avoid MySQL reserved words; do not rely on platform-specific case handling or perpetual quoting.
- Choose types from domain range and comparison semantics. Use exact numeric types for exact values and preserve existing identifier types.
- `NOT NULL` requires a valid creation/backfill path. Do not add sentinel values merely to avoid modelling nullability.
- A business key whose uniqueness is an invariant **MUST** have a database unique constraint/index; an application-side existence check alone is race-prone.
- Compare null with `IS NULL`/`IS NOT NULL`; avoid implicit type conversion in joins and predicates.
- Understand aggregate null semantics: `COUNT(*)` counts rows, `COUNT(column)` skips nulls, and `SUM` over no non-null rows can return null; map the result deliberately.
- Every update/delete **MUST** have an intentional predicate and expected affected-row behavior. Broad maintenance statements require an explicit migration rationale.
- Indexes **MUST** support observed equality/range/join/order predicates using the leftmost-prefix rule. Avoid redundant indexes and functions on indexed predicate columns.
- Leading-wildcard searches cannot use a normal B-tree prefix efficiently; require an explicit bounded search strategy or the project's search subsystem instead of hiding a table scan.
- Lock rows in a consistent order, keep lock scope short, and use conditional updates when they can express the concurrency invariant without a wide lock.
- Seed and fixture SQL **MUST NOT** create usable default credentials, plaintext tokens, or production-like secrets.
- Before corrective update/delete DML, express the same predicate as a `SELECT` and verify the expected row set/count; make the migration's scope reviewable.
- Mapper binding, column selection, query cardinality, pagination, and N+1 rules are owned by `backend/05-mysql-database.md`; do not redefine them here.
