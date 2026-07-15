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

- Read the root guide plus every affected subtree guide before deciding where the contract is owned.
- Use graph tracing for symbols and callers, then search serialized field and endpoint literals to find consumers that static call edges cannot reveal.
- Create a producer-consumer matrix for each changed request, response, event, or shared type. Include mappings, fixtures, and contract-focused tests in the matrix.
- Compare the before and after wire shape explicitly and record any compatibility boundary that cannot be updated atomically.
- Run the verification commands from each affected guide and inspect the final diff against the matrix for omissions.
