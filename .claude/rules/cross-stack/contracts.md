---
paths:
  - "services/**/src/main/java/**/controller/**/*.java"
  - "{backend-auth,backend-admin,backend-app}/src/main/java/**/controller/**/*.java"
  - "services/**/src/main/java/**/adapter/in/web/**/*.java"
  - "{backend-auth,backend-admin,backend-app}/src/main/java/**/adapter/in/web/**/*.java"
  - "services/**/src/main/java/**/*DTO.java"
  - "{backend-auth,backend-admin,backend-app}/src/main/java/**/*DTO.java"
  - "services/**/src/main/java/**/*VO.java"
  - "{backend-auth,backend-admin,backend-app}/src/main/java/**/*VO.java"
  - "services/**/src/main/java/**/*Projection.java"
  - "{backend-auth,backend-admin,backend-app}/src/main/java/**/*Projection.java"
  - "services/**/src/main/java/**/*Request.java"
  - "{backend-auth,backend-admin,backend-app}/src/main/java/**/*Request.java"
  - "services/**/src/main/java/**/*Response.java"
  - "{backend-auth,backend-admin,backend-app}/src/main/java/**/*Response.java"
  - "apps/console/src/api/**/*.ts"
  - "apps/console/src/types/**/*.ts"
  - "apps/management/src/api/**/*.ts"
  - "apps/management/src/types/**/*.ts"
  - "packages/domain-types/**/*.ts"
  - "packages/sandbox-types/**/*.ts"
kind: rules
summary: 'Cross-stack API contracts (DTOs, VOs, frontend types).'
---

# Cross-stack contract changes

- Read the root guide plus every affected subtree guide before deciding where the contract is owned.
- Use graph tracing for symbols and callers, then search serialized field and endpoint literals to find consumers that static call edges cannot reveal.
- Create a producer-consumer matrix for each changed request, response, event, or shared type. Include mappings, fixtures, and contract-focused tests in the matrix.
- Compare the before and after wire shape explicitly and record any compatibility boundary that cannot be updated atomically.
- Run the verification commands from each affected guide and inspect the final diff against the matrix for omissions.
