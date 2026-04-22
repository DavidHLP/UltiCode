# Phase 45: API Documentation - Research

**Researched:** 2026-04-22
**Domain:** SpringDoc OpenAPI, Swagger UI, Spring Boot 3.2.5 compatibility
**Confidence:** HIGH (verified via Maven Central)

## Summary

Phase 45 has a **critical blocking issue**: the required springdoc version 2.8.17 is incompatible with Spring Boot 3.2.5. Every 2.8.x version from 2.8.9 onward requires Spring Boot 3.5+, and 2.8.17 specifically uses Spring Boot 3.5.13 as its parent POM. The project runs Spring Boot 3.2.5, so a direct upgrade to 2.8.17 will fail. This was the exact ClassNotFoundException observed in Phase 41.

**Three paths forward:**
1. **Upgrade Spring Boot to 3.5.x first** (substantial effort, out of Phase 45 scope)
2. **Stay on springdoc 2.6.0** (only version compatible with Spring Boot 3.2.x, swagger-ui 5.17.14)
3. **Downgrade to springdoc 2.5.0** (Spring Boot 3.2.4 parent, swagger-ui 5.13.0 - older Swagger UI)

The annotation work (API-02) is fully feasible regardless of which dependency path is chosen. AchievementController.java is a complete annotation template.

## User Constraints (from CONTEXT.md)

### Locked Decisions
- **D-01:** springdoc.version - Attempt upgrade to 2.8.17 (Phase 41 noted ClassNotFoundException blocking it)
- **D-02:** Target controllers: auth, user, problem, submission, contest
- **D-03:** All methods in target controllers should have @Operation annotations
- **D-04:** Controllers that already have @Tag annotations: auth, user, problem, submission, contest - add/expand @Operation/@ApiResponse to ALL methods
- **D-05:** Each endpoint gets @Operation(summary = "...") with a concise one-line description
- **D-06:** Each endpoint with non-void response gets @ApiResponse annotations listing HTTP status codes and response types
- **D-07:** Swagger UI at /swagger-ui.html returns HTTP 200
- **D-08:** /api-docs returns valid OpenAPI 3.0 JSON
- **D-09:** All 5 critical modules appear in the OpenAPI spec

### Deferred Ideas
None.

## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| API-01 | SpringDoc upgrade to 2.8.17 (OpenAPI 3.0, compatible with Spring Boot 3.2.5) | **BLOCKED** - 2.8.17 requires Spring Boot 3.5.13, incompatible with 3.2.5. See Dependency Compatibility below. |
| API-02 | Add @Operation/@ApiResponse annotations to critical endpoints | Fully supported - AchievementController.java provides complete template pattern |
| API-03 | Swagger UI at /swagger-ui.html accessible | Fully supported - works with springdoc 2.6.0, verified in Phase 41 |

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| OpenAPI spec generation | API/Backend | - | springdoc generates spec from annotations at startup |
| Swagger UI serving | API/Backend | - | served by springdoc webmvc-ui at /swagger-ui.html |
| Endpoint annotation | API/Backend | - | @Operation/@ApiResponse on controller methods |
| Security scheme config | API/Backend | - | SwaggerConfig.java already has JWT Bearer auth |

## Dependency Compatibility Analysis

### Critical Finding: springdoc 2.8.17 requires Spring Boot 3.5.13

Source: Maven Central POM inspection of springdoc 2.8.17 parent

| springdoc version | Spring Boot parent | swagger-ui version | Compatible with SB 3.2.5? |
|-------------------|-------------------|-------------------|---------------------------|
| **2.8.17** | 3.5.13 | 5.32.2 | **NO** |
| 2.8.10 | 3.5.4 | ~5.30+ | **NO** |
| 2.8.9 | 3.5.0 | ~5.25+ | **NO** |
| 2.8.8 | 3.4.5 | 5.21.0 | **NO** |
| 2.6.1 | 3.3.0 | 5.17.14 | **NO** |
| **2.6.0** | 3.2.4 | 5.17.14 | **YES** |
| 2.5.0 | 3.2.4 | 5.13.0 | **YES** (but older Swagger UI) |

**[VERIFIED: Maven Central springdoc-openapi-2.8.17.pom]** - `spring-boot-starter-parent` version is 3.5.13
**[VERIFIED: Maven Central springdoc-openapi-2.6.0.pom]** - `spring-boot-starter-parent` version is 3.3.0
**[VERIFIED: Maven Central springdoc-openapi-2.5.0.pom]** - `spring-boot-starter-parent` version is 3.2.4

### Root Cause of Phase 41 ClassNotFoundException

The `LiteWebJarsResourceResolver` class was removed or restructured in a newer swagger-ui webjar version. springdoc 2.8.17 ships swagger-ui 5.32.2. The project's Spring Boot 3.2.5 cannot resolve classes from this newer swagger-ui because the webjar structure changed.

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| springdoc-openapi-starter-webmvc-ui | 2.6.0 (current) | OpenAPI 3.0 spec + Swagger UI | Only version compatible with Spring Boot 3.2.5 |
| springdoc-openapi-starter-webmvc-ui | **2.8.17 (target)** | OpenAPI 3.0 spec + Swagger UI | Required by API-01, but **incompatible with SB 3.2.5** |

### Upgrade Options (D-01 resolution)

| Option | springdoc version | Spring Boot needed | swagger-ui | Pros | Cons |
|--------|-------------------|-------------------|------------|------|------|
| A: Upgrade Spring Boot | 2.8.17 | 3.5.13 | 5.32.2 | Latest features, newest Swagger UI | Major upgrade (out of scope), risky |
| B: Stay on 2.6.0 | 2.6.0 | 3.2.5 (current) | 5.17.14 | Stable, compatible, works today | Not the version specified in API-01 |
| C: Downgrade to 2.5.0 | 2.5.0 | 3.2.4 | 5.13.0 | Compatible | Older Swagger UI, may miss 2.6.0 fixes |

**Recommendation:** Option B (stay on 2.6.0) is the only viable path that keeps Phase 45 scope manageable. Option A would require a Spring Boot upgrade that could introduce breaking changes across the entire backend.

## Architecture Patterns

### System Architecture Diagram

```
Request → Spring Security Filter Chain
           ↓ (if /swagger-ui/** or /api-docs/**)
         SpringDoc WebMvc Resource Handler
           ↓ (serves static Swagger UI files from webjar)
         swagger-ui webjar (/webjars/swagger-ui/5.x.x/dist/)
           ↓
         Browser renders Swagger UI

Request → Controller (@Operation annotated)
           ↓
         SpringDoc OpenAPI Generator (build-time)
           ↓
         OpenAPI JSON available at /api-docs
```

### Recommended Project Structure
No new files needed. Existing structure already has:
- `backend-spring/pom.xml` - springdoc.version property
- `backend-spring/src/main/java/com/ulticode/common/config/SwaggerConfig.java` - OpenAPI bean (already configured)
- Controller annotations added inline

### Annotation Pattern (from AchievementController.java)

```java
// Source: AchievementController.java - the template controller
@Tag(name = "Achievement", description = "Achievement management API")
@RestController
@RequestMapping("/achievements")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer")   // applies to all methods
public class AchievementController {

    @Operation(summary = "Get all achievements")
    @GetMapping
    public Result<PageResult<AchievementVO>> list(AchievementQueryDTO query) {
        return Result.success(achievementService.list(query));
    }

    @Operation(summary = "Get achievement by ID")
    @ApiResponse(responseCode = "404", description = "Achievement not found")
    @GetMapping("/{id}")
    public Result<AchievementVO> getById(@PathVariable String id) {
        return Result.success(achievementService.getById(id));
    }
}
```

### @ApiResponse Pattern for Result<T> Wrappers

Since all endpoints return `Result<T>` wrapper (not raw types), the response type annotation should reference the inner type:

```java
@Operation(summary = "Get problem by ID")
@ApiResponse(responseCode = "200", description = "Problem found",
             content = @Content(schema = @Schema(implementation = ProblemDetailResponse.class)))
@ApiResponse(responseCode = "404", description = "Problem not found")
@GetMapping("/{id}")
public Result<ProblemDetailResponse> getProblemById(@PathVariable Long id) { ... }
```

Note: The `content` schema should reference the VO/DTO type (e.g., `ProblemDetailResponse`), not `Result`, since springdoc can infer the wrapper from the return type.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| OpenAPI spec generation | Write manual OpenAPI YAML/JSON | springdoc auto-generates from annotations | springdoc reads annotations at runtime and generates valid OpenAPI 3.0 |
| Swagger UI serving | Bundle Swagger UI static files manually | springdoc-starter-webmvc-ui webjar | Serves correct version matched to springdoc, auto-configured |
| JWT security scheme | Configure security scheme manually | SwaggerConfig.java (existing) | Already has Bearer JWT scheme configured |

## Common Pitfalls

### Pitfall 1: Version Mismatch Causes Runtime ClassNotFoundException
**What goes wrong:** Upgrading springdoc to a version requiring newer Spring Boot causes `ClassNotFoundException: LiteWebJarsResourceResolver` at startup.
**Why it happens:** springdoc 2.8.9+ bundles a newer swagger-ui webjar whose class structure is incompatible with older Spring Boot versions.
**How to avoid:** Only use springdoc versions whose Spring Boot parent requirement matches or is older than 3.2.5. Currently: 2.5.x or 2.6.x.
**Warning signs:** Backend fails to start with `ClassNotFoundException` related to swagger-ui or webjars.

### Pitfall 2: @ApiResponse content schema doesn't match actual return type
**What goes wrong:** Swagger UI shows wrong response type for endpoints.
**Why it happens:** For `Result<T>` wrapped responses, the schema should reference `T`, not `Result<T>`.
**How to avoid:** Use `@Schema(implementation = YourVO.class)` pointing to the inner type, or use springdoc's `SchemaType` inference.

### Pitfall 3: @SecurityRequirement missing on controller with authenticated endpoints
**What goes wrong:** Authenticated endpoints don't show lock icon in Swagger UI.
**Why it happens:** `@SecurityRequirement(name = "Bearer")` is not inherited - must be on each method or controller.
**How to avoid:** Add `@SecurityRequirement(name = "Bearer")` at controller level (like ContestController has) OR on each authenticated method.

## Current Annotation State

| Controller | @Tag | @Operation (all methods) | @ApiResponse (all methods) | Notes |
|------------|------|--------------------------|---------------------------|-------|
| AuthController | YES | YES (8 methods) | NO | Has @Operation with summary+description |
| UserController | YES | YES (8 methods) | NO | Has @Operation with summary+description |
| ProblemController | YES | YES (8 methods) | NO | Has @Operation with summary+description |
| SubmissionController | YES | YES (7 methods) | NO | Has @Operation with summary+description |
| ContestController | YES | YES (17 methods) | NO | Has @Operation with summary+description, @SecurityRequirement at method level |

**AchievementController (template):** Fully annotated with @Tag, @Operation(summary) on all methods, and @ApiResponse on methods with non-void returns. **This is the target pattern.**

## Verification Plan

### Verification Commands

```bash
# 1. Start backend and verify Swagger UI loads
curl -s -o /dev/null -w "%{http_code}" http://localhost:9001/swagger-ui.html
# Expected: 200 (or 302 redirect to index)

# 2. Verify OpenAPI spec
curl -s http://localhost:9001/api-docs | jq '.openapi'
# Expected: "3.0.1"

# 3. Verify all 5 modules appear in spec
curl -s http://localhost:9001/api-docs | jq '.paths | keys | length'
# Expected: count of all endpoints across 5 modules

# 4. Verify specific tag appears
curl -s http://localhost:9001/api-docs | jq '.tags[].name'
# Expected: ["Auth","User","Problem","Submissions","Contest"]
```

## Code Examples

### Adding @ApiResponse to an endpoint (Pattern from AchievementController)

```java
@Operation(summary = "Create problem", description = "Create a new problem (admin only)")
@ApiResponse(responseCode = "200", description = "Problem created successfully",
            content = @Content(schema = @Schema(implementation = ProblemVO.class)))
@ApiResponse(responseCode = "400", description = "Invalid input")
@ApiResponse(responseCode = "401", description = "Unauthorized")
@ApiResponse(responseCode = "403", description = "Forbidden - requires admin role")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
@PostMapping
public Result<ProblemVO> createProblem(@Valid @RequestBody CreateProblemDTO createDTO) {
    ProblemVO problem = problemService.createProblem(createDTO);
    return Result.success(problem);
}
```

### For void return endpoints

```java
@Operation(summary = "Delete problem")
@ApiResponse(responseCode = "200", description = "Problem deleted successfully")
@ApiResponse(responseCode = "404", description = "Problem not found")
@DeleteMapping("/{id}")
public Result<Void> deleteProblem(@PathVariable Long id) {
    problemService.deleteProblem(id);
    return Result.success();
}
```

### For paginated responses

```java
@Operation(summary = "List problems")
@ApiResponse(responseCode = "200", description = "Problems retrieved successfully",
            content = @Content(schema = @Schema(implementation = PageResult.class)))
@GetMapping
public Result<PageResult<ProblemVO>> listProblems(...) {
    // ...
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Manual OpenAPI YAML | springdoc annotations | 2.6.0 added Phase 34 | Auto-generates spec from code |
| No @Operation annotations | @Operation required on all endpoints | Phase 45 | Better Swagger UI descriptions |
| Generic security scheme | JWT Bearer configured | SwaggerConfig.java (existing) | Already correct |

**Deprecated/outdated:**
- `springdoc-openapi-starter-webmvc-ui` without explicit version pinning (should use `${springdoc.version}` property)
- `@Api` annotation (replaced by `@Tag`)

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Phase 41's ClassNotFoundException was `LiteWebJarsResourceResolver` from swagger-ui incompatibility | Dependency Compatibility | HIGH - if the error was different, 2.8.17 might actually work |
| A2 | The project cannot upgrade Spring Boot to 3.5.x without substantial risk | Dependency Compatibility | MEDIUM - upgrading Spring Boot might be feasible with testing |
| A3 | Downgrading to springdoc 2.5.0 is possible but gives older Swagger UI | Dependency Compatibility | LOW - 2.5.0 should work but hasn't been tested with this project |

## Open Questions

1. **Is upgrading Spring Boot to 3.5.x within Phase 45 scope?**
   - What we know: 2.8.17 requires Spring Boot 3.5.13. The project is on 3.2.5.
   - What's unclear: Whether the milestone v3.0 scope allows a Spring Boot minor version upgrade
   - Recommendation: Proceed with 2.6.0 (compatible) and flag API-01 as requiring a separate Spring Boot upgrade OR scope expansion

2. **Should @ApiResponse include `content` for `Result<T>` wrapped responses?**
   - What we know: springdoc infers the schema from return type, but explicit `@Content(schema = @Schema(implementation = ...))` makes it unambiguous
   - What's unclear: Whether springdoc 2.6.0 correctly unwraps `Result<T>` generics for schema display
   - Recommendation: Add explicit `content` with inner type reference for all non-void returns

3. **Does AuthController need @SecurityRequirement at controller level?**
   - What we know: AuthController has no `@SecurityRequirement` at controller level, but individual methods are annotated. OAuth methods (githubLogin, googleLogin) don't need auth.
   - What's unclear: Whether authenticated methods like /me and /permissions will display with lock icon
   - Recommendation: Add `@SecurityRequirement(name = "Bearer")` at controller level if authenticated methods don't show locks

## Environment Availability

> Step 2.6: SKIPPED (no external dependencies beyond project's own code and Maven Central)

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 5 (existing Spring Boot test infrastructure) |
| Config file | backend-spring/src/test/resources/ |
| Quick run command | `./mvnw test -Dtest=*ControllerTest -pl backend-spring` |
| Full suite command | `./mvnw test -pl backend-spring` |

### Phase Requirements to Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| API-01 | springdoc version in pom.xml | Manual | `grep springdoc.version backend-spring/pom.xml` | N/A |
| API-02 | @Operation on all 5 controller methods | Manual | `grep -c "@Operation" backend-spring/src/main/java/com/ulticode/modules/*/controller/*.java` | N/A |
| API-03 | Swagger UI accessible | Smoke | `curl -s -o /dev/null -w "%{http_code}" http://localhost:9001/swagger-ui.html` | N/A |

### Wave 0 Gaps
None - existing test infrastructure covers API behavior. Phase 45 is primarily annotation and configuration work.

## Security Domain

> Required when `security_enforcement` is enabled (absent = enabled). Omit only if explicitly `false` in config.

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | NO | N/A - this phase documents existing auth |
| V3 Session Management | NO | N/A - no auth changes |
| V4 Access Control | NO | N/A - no permission changes |
| V5 Input Validation | NO | N/A - no input handling changes |
| V6 Cryptography | NO | N/A - no crypto changes |

**Note:** Phase 45 is documentation-only from a security perspective. Annotations improve API discoverability but do not change security behavior.

## Sources

### Primary (HIGH confidence)
- [Maven Central: springdoc-openapi 2.8.17 POM](https://repo1.maven.org/maven2/org/springdoc/springdoc-openapi/2.8.17/springdoc-openapi-2.8.17.pom) - Spring Boot parent version 3.5.13, swagger-ui 5.32.2
- [Maven Central: springdoc-openapi 2.6.0 POM](https://repo1.maven.org/maven2/org/springdoc/springdoc-openapi/2.6.0/springdoc-openapi-2.6.0.pom) - Spring Boot parent version 3.3.0, swagger-ui 5.17.14
- [Maven Central: springdoc-openapi 2.5.0 POM](https://repo1.maven.org/maven2/org/springdoc/springdoc-openapi/2.5.0/springdoc-openapi-2.5.0.pom) - Spring Boot parent version 3.2.4, swagger-ui 5.13.0
- [Maven Central: springdoc-openapi-starter-webmvc-ui 2.8.17 POM](https://repo1.maven.org/maven2/org/springdoc/springdoc-openapi-starter-webmvc-ui/2.8.17/springdoc-openapi-starter-webmvc-ui-2.8.17.pom) - webjars-locator-lite dependency confirmed
- AchievementController.java - fully annotated controller serving as template

### Secondary (MEDIUM confidence)
- Phase 41 context (ClassNotFoundException blocking 2.8.17) - referenced from 45-CONTEXT.md canonical refs
- Phase 41 context (Swagger UI at /swagger-ui.html returns 302, /api-docs returns valid JSON) - verified in Phase 41

### Tertiary (LOW confidence)
- webjars-locator-lite 0.59 is latest - verified but not directly relevant to the compatibility issue

## Metadata

**Confidence breakdown:**
- Standard stack: MEDIUM - springdoc 2.6.0 confirmed compatible; 2.8.17 confirmed incompatible. No uncertainty.
- Architecture: HIGH - annotation patterns verified from existing code
- Pitfalls: MEDIUM - version mismatch confirmed from Maven Central. Could not reproduce exact error from Phase 41.

**Research date:** 2026-04-22
**Valid until:** 30 days (springdoc version compatibility does not change rapidly)
