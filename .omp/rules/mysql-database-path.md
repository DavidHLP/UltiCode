---
description: "Persistence changes"
globs:
  - "services/**/src/main/java/**/*Mapper.java"
  - "services/**/src/main/java/**/mapper/**/*.java"
  - "init-db/migrations/**/*.sql"
  - "init-db/baseline/**/*.sql"
---

# Persistence changes

- Use the existing annotation-based MyBatis boundary and bound SQL values. Request-controlled identifiers need a closed allowlist; preserve authorization and ownership predicates.
- Keep queries within the service's owned schema. Use explicit columns and typed results for new queries; preserve existing mappings when editing.
- Give pagination deterministic ordering and bound batch inputs. Define empty-input behavior for collection parameters.
- Enforce business uniqueness in the database and check affected rows when concurrent or missing state changes the outcome. Keep update/delete predicates intentional; verify the target data before corrective DML.
