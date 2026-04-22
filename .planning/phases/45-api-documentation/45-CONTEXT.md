# Phase 45: API Documentation - Context

**Gathered:** 2026-04-22
**Status:** Ready for planning

<domain>
## Phase Boundary

Upgrade SpringDoc to 2.8.17, add @Operation/@ApiResponse annotations to critical endpoints (auth, user, problem, submission, contest), and ensure Swagger UI loads at /swagger-ui.html with valid OpenAPI 3.0 spec.

</domain>

<decisions>
## Implementation Decisions

### Dependency Upgrade
- **D-01:** springdoc.version — Attempt upgrade to 2.8.17. Phase 41 noted ClassNotFoundException (LiteWebJarsResourceResolver) blocking the upgrade. Researcher to investigate whether 2.8.17 is now compatible with Spring Boot 3.2.5, or if a workaround exists. If truly incompatible, fall back to latest compatible version and document the constraint.

### Annotation Scope
- **D-02:** Target controllers — auth, user, problem, submission, contest (as specified in SUCCESS CRITERIA)
- **D-03:** All methods in target controllers should have @Operation annotations (not just some endpoints)
- **D-04:** Controllers that already have @Tag annotations: auth, user, problem, submission, contest — add/expand @Operation/@ApiResponse to ALL methods

### Annotation Depth
- **D-05:** Each endpoint gets @Operation(summary = "...") with a concise one-line description
- **D-06:** Each endpoint with non-void response gets @ApiResponse annotations listing HTTP status codes and response types

### Verification
- **D-07:** Swagger UI at /swagger-ui.html returns HTTP 200
- **D-08:** /api-docs returns valid OpenAPI 3.0 JSON
- **D-09:** All 5 critical modules appear in the OpenAPI spec

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Requirements
- `.planning/REQUIREMENTS.md` §API-01, API-02, API-03 — Phase 45 acceptance criteria
- `.planning/ROADMAP.md` §Phase 45 — Phase goal and success criteria

### Prior Phase Context
- `.planning/phases/41-dependency-upgrades/41-CONTEXT.md` — springdoc 2.8.17 blocked by ClassNotFoundException (D-01)
- `.planning/phases/44-testcontainers-upgrade/44-CONTEXT.md` — Most recent phase context

### Backend Conventions
- `.planning/codebase/CONVENTIONS.md` — Java naming and code style conventions
- `backend-spring/pom.xml` — Current springdoc.version (2.6.0), version property pattern
- `backend-spring/src/main/java/com/ulticode/common/config/SwaggerConfig.java` — Existing SwaggerConfig with JWT security scheme
- `backend-spring/src/main/java/com/ulticode/modules/achievement/controller/AchievementController.java` — Example of fully-annotated controller (@Tag + @Operation + @ApiResponse)
- `backend-spring/src/main/java/com/ulticode/modules/auth/controller/AuthController.java` — Target controller with existing @Tag but incomplete @Operation

### Test Patterns
- `backend-spring/src/test/java/com/ulticode/modules/auth/controller/AuthControllerTest.java` — Existing test pattern

</canonical_refs>

<codebase_context>
## Existing Code Insights

### Reusable Assets
- **AchievementController.java**: Fully annotated controller — @Tag + @Operation(summary) on all endpoints, @ApiResponse for response codes. Can serve as annotation template.
- **SwaggerConfig.java**: Already configures OpenAPI with JWT Bearer auth scheme — security context already set up.

### Established Patterns
- **@Tag on controller class**: Groups endpoints under a named category in Swagger UI
- **@Operation on methods**: Provides summary description for each endpoint
- **@ApiResponse**: Documents HTTP response codes and response types

### Integration Points
- **pom.xml**: springdoc.version property at top — single place to update version
- **SwaggerConfig.java**: Already creates OpenAPI bean — may need updates for 2.8.17 compatibility

</codebase_context>

<specifics>
## Specific Ideas

springdoc 2.6.0 was added in Phase 34 specifically to fix compatibility with Spring Boot 3.2.5 (regression from 2.8.17). Phase 45 explicitly targets 2.8.17 as the goal. The researcher should investigate whether springdoc 2.8.17 actually supports Spring Boot 3.2.5 now (perhaps through a different springdoc starter artifact or configuration approach).

</specifics>

<deferred>
## Deferred Ideas

None — Phase 45 scope is well-defined.

</deferred>
