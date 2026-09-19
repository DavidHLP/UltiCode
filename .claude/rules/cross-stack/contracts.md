---
paths:
  - "services/api/**/src/main/java/**/*.java"
  - "services/**/src/main/java/**/controller/**/*.java"
  - "services/**/src/main/java/**/adapter/in/web/**/*.java"
  - "services/**/src/main/java/**/*DTO.java"
  - "services/**/src/main/java/**/*VO.java"
  - "services/**/src/main/java/**/*Projection.java"
  - "services/**/src/main/java/**/*Request.java"
  - "services/**/src/main/java/**/*Response.java"
  - "apps/console/src/api/**/*.ts"
  - "apps/console/src/types/**/*.ts"
  - "apps/management/src/api/**/*.ts"
  - "apps/management/src/types/**/*.ts"
  - "packages/domain-types/**/*.ts"
  - "packages/sandbox-types/**/*.ts"
---

# Contract changes

- Identify the actual producers and consumers of a changed wire contract, including serialized field names that symbol references may miss.
- Preserve the established HTTP/RPC envelopes and field mappings. Update affected Java contracts, shared types and clients together; state rollout compatibility when they cannot change atomically.
- Check the changed contract and affected consumers. A separate matrix or full cross-stack build is useful only when the change's scope warrants it.
