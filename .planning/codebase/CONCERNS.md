# UltiCode Codebase Concerns

Known bugs, technical debt, and improvement items identified from planning docs, CI runs, and code analysis.

---

## BUGS

### B-01: Swagger/Springdoc Disabled
**Severity**: HIGH
**Module**: backend-spring
**File**: `backend-spring/src/main/java/com/ulticode/common/config/SwaggerConfig.java`
**Status**: Temporarily disabled

**Problem**: Swagger/OpenAPI documentation is completely disabled because springdoc 2.x is incompatible with Spring Boot 3.2.5 (Missing `LiteWebJarsResourceResolver` class).

**Current State**: `SwaggerConfig` class is fully commented out with note:
```java
// TEMPORARILY DISABLED - springdoc 2.x incompatible with Spring Boot 3.2.5
// Re-enable when springdoc supports SB3
```

**Impact**: No API documentation available at `/swagger-ui.html` or `/api-docs`.

**Fix Needed**: Upgrade to a compatible springdoc version when available, or find alternative API documentation solution.

---

### B-02: Admin Forum Stats Return Hardcoded Zeros
**Severity**: MEDIUM
**Module**: backend-spring
**File**: `backend-spring/src/main/java/com/ulticode/modules/admin/service/impl/AdminForumServiceImpl.java`
**Lines**: 276-278

**Problem**: Forum post statistics are hardcoded to 0 instead of querying actual data:
```java
vo.setCommentCount(0); // TODO: Query from forum_comments table
vo.setUpvotes(0); // TODO: Query from forum_votes table
vo.setDownvotes(0); // TODO: Query from forum_votes table
```

**Impact**: Admin dashboard shows incorrect forum engagement metrics.

**Fix Needed**: Implement actual queries against `forum_comments` and `forum_votes` tables.

---

## TECHNICAL DEBT

### TD-01: PM2 Environment Variable Parsing
**Severity**: MEDIUM
**Module**: Infrastructure
**File**: `ecosystem.config.cjs`
**Lines**: 1-21

**Problem**: Custom .env file parser uses string manipulation that may fail with:
- Quoted values with nested quotes
- Values containing `=` characters
- Multiline values
- Special characters in passwords/tokens

**Current State**: Manual implementation instead of using proven `dotenv` package.

**Fix Needed**: Replace custom parser with proper `dotenv` package or use PM2's built-in env support.

---

### TD-02: Maven Build Order Dependency
**Severity**: MEDIUM
**Module**: backend-spring / recommendation
**Files**: `backend-spring/pom.xml`, `recommendation/pom.xml`

**Problem**: `backend-spring` depends on `com.ulticode:recommend-api:jar:1.0.0` which is a local Maven module. If `recommendation` is not installed first, backend build fails.

**Impact**: CI workflows and fresh builds require specific build order.

**Fix Needed**: Either:
1. Publish `recommend-api` to a private Maven repository
2. Use Maven ` reactor` to build in correct order
3. Document the build order requirement

---

### TD-03: Dubbo Configuration Complexity
**Severity**: MEDIUM
**Module**: recommendation service
**Files**: Dubbo configs in recommendation module

**Problem**: Dubbo WARN messages about `empty url address list` and `empty configurators` require specific configuration workarounds:
```
enable-empty-protection: "true"  # Must be in dubbo.registry.parameters map
```

**Impact**: Added complexity in configuration, potential runtime issues if misconfigured.

**Fix Needed**: Document exact configuration requirements and verify in production.

---

## CI/CD ISSUES

### CI-01: Flyway Download URL Obsolete
**Severity**: HIGH
**Module**: CI/CD
**File**: `.github/workflows/ci.yml`

**Problem**: CI workflow downloads Flyway from `https://download.redgate.com/flyway/...` but Flyway has moved to Redgate domain. URL returns 404.

**Status**: Issue identified in CI run 24601704434, fix was proposed but verification needed.

**Fix Needed**: Update Flyway download URL in CI workflow.

---

### CI-02: Build Artifact Caching Gaps
**Severity**: LOW
**Module**: CI/CD

**Problem**: Maven and pnpm dependencies are downloaded on every CI run instead of being cached effectively.

**Impact**: Longer CI execution times.

**Fix Needed**: Implement proper caching strategy for Maven/pnpm artifacts.

---

## IMPROVEMENT ITEMS

### IMP-01: Planning Doc Status Inconsistency
**Severity**: LOW
**Module**: Documentation
**File**: `.planning/ROADMAP.md`

**Problem**: Phase 15 shows "Plans complete but table shows Not Started" status discrepancy.

**Status**: Noted in STATE.md timeline on 2026-04-19.

**Fix Needed**: Verify and correct ROADMAP.md status table.

---

### IMP-02: Phase 15 Test Coverage
**Severity**: MEDIUM
**Module**: Testing
**File**: Phase 15 UAT document

**Problem**: Phase 15 has 10 testable deliverables but no corresponding test files created yet based on the test directory analysis.

**Status**: UAT file created with testable deliverables documented.

**Fix Needed**: Create actual test cases matching the 15-UAT.md deliverables.

---

### IMP-03: Missing Test Directory Structure
**Severity**: LOW
**Module**: console frontend
**File**: `console/src/test/`

**Problem**: Previously `vitest.config.ts` referenced `./test/setup.ts` which did not exist, causing test failures in CI. While this was fixed, the actual test directory structure remains minimal.

**Fix Needed**: Verify proper test setup exists and add tests for critical user flows.

---

## ARCHITECTURE CONCERNS

### ARCH-01: Recommendation Service Optional Dependency
**Severity**: INFO
**Module**: Architecture

**Problem**: The recommendation service (Dubbo3 + Spark) is marked as optional but the backend still has compile-time dependency on `recommend-api` module.

**Current Behavior**: `RECOMMENDATION_ENABLED=true` env var controls runtime behavior but compile-time coupling exists.

**Note**: This is a known architecture decision documented in CLAUDE.md.

---

## ALREADY RESOLVED (for reference)

These issues were identified and fixed but are documented for historical awareness:

- **ESLint version conflict**: ESLint 10.x incompatible with @typescript-eslint/utils 8.x - Fixed by downgrading to eslint ^9.30.1
- **vitest setupFiles reference**: References non-existent `console/src/test/setup.ts` - Fixed by removing setupFiles
- **recommend-api not installed**: CI Backend Build failed - Fixed by ensuring proper build order
- **OAuthService Spring 6.x compatibility**: Fixed for Spring Framework 6.x compatibility
- **springdoc LiteWebJarsResourceResolver**: Downgraded from 2.7.0 to 2.6.0
- **CI lockfile issues**: Multiple CI fixes applied and verified (commit aa51e0404)

---

## PRIORITY SUMMARY

| ID | Severity | Category | Item |
|----|----------|----------|------|
| B-01 | HIGH | Bug | Swagger disabled |
| CI-01 | HIGH | CI/CD | Flyway URL obsolete |
| B-02 | MEDIUM | Bug | Forum stats hardcoded |
| TD-01 | MEDIUM | Tech Debt | PM2 env parsing |
| TD-02 | MEDIUM | Tech Debt | Maven build order |
| TD-03 | MEDIUM | Tech Debt | Dubbo configuration |
| IMP-01 | LOW | Improvement | Doc inconsistency |
| IMP-02 | MEDIUM | Improvement | Phase 15 tests |
| IMP-03 | LOW | Improvement | Test structure |
| CI-02 | LOW | CI/CD | Build caching |
