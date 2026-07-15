---
paths:
  - "backend-spring/src/main/java/**/controller/**/*.java"
  - "backend-spring/src/main/java/**/*DTO.java"
  - "backend-spring/src/main/java/**/*VO.java"
  - "backend-spring/src/main/java/**/*Projection.java"
  - "backend-spring/src/main/java/**/*Request.java"
  - "backend-spring/src/main/java/**/*Response.java"
  - "console/src/api/**/*.ts"
  - "console/src/types/**/*.ts"
  - "management/src/api/**/*.ts"
  - "management/src/types/**/*.ts"
  - "shared/domain-types/**/*.ts"
  - "shared/sandbox-types/**/*.ts"
---

# Cross-stack contract changes

- Trace the producer from controller input through service and mapping to the response envelope, then trace every Console, Management, and shared-type consumer.
- Preserve field names, nullability, identifiers, enum values, pagination metadata, and error semantics across the full path.
- Update producers, shared types, application adapters, fixtures, and contract-focused tests in the same change; do not leave temporary casts or duplicate aliases as hidden compatibility layers.
- Keep an app-specific contract local. Move a type into `shared/` only when both applications need the same stable meaning.
- If compatibility cannot be preserved atomically, document and implement an explicit rollout boundary rather than relying on deployment order by accident.
