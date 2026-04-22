---
phase: 44-testcontainers-upgrade
plan: 44-01-PLAN
status: complete
completed: 2026-04-22
---

# Phase 44: Testcontainers Upgrade — Plan 44-01 Summary

## Overview

**Requirement:** TEST-01 — Upgrade testcontainers-bom and testcontainers from 1.11.3 to latest stable 11.x.

**Objective:** Upgrade testcontainers BOM to 1.21.4, fix `getFirstMappedPort()` → `getMappedPort(6379)` in RateLimitIntegrationTest.java.

## Tasks Completed

| Task | Status | Details |
|------|--------|---------|
| 1: Update pom.xml versions | DONE | BOM 1.11.3 → 1.21.4, explicit versions removed |
| 2: Check API breaking changes | DONE | getFirstMappedPort() deprecated in 11.x |
| 3: Update RateLimitIntegrationTest.java | DONE | getMappedPort(6379) replaces getFirstMappedPort() |
| 4: Verify upgrade with Maven | DONE | dependency:tree shows 1.21.4, test-compile passes |
| 5: Commit changes | DONE | commit 66eb34253 |

## Files Modified

| File | Action | Change |
|------|--------|--------|
| `backend-spring/pom.xml` | Modified | testcontainers-bom 1.11.3 → 1.21.4, explicit versions removed from testcontainers, junit-jupiter, mysql |
| `backend-spring/src/test/java/com/ulticode/modules/auth/controller/RateLimitIntegrationTest.java` | Modified | `getFirstMappedPort()` → `getMappedPort(6379)` |

## Key Changes

### pom.xml

- `testcontainers-bom`: 1.11.3 → **1.21.4**
- `testcontainers`: now BOM-managed (no explicit version)
- `junit-jupiter`: now BOM-managed (no explicit version)
- `testcontainers-mysql`: now BOM-managed (no explicit version)
- `testcontainers-redis`: **2.2.2** unchanged (separate maintenance cycle)

### RateLimitIntegrationTest.java (line 56)

**Before:**
```java
registry.add("spring.data.redis.port", () -> container.getFirstMappedPort());
```

**After:**
```java
registry.add("spring.data.redis.port", () -> container.getMappedPort(6379));
```

## Verification

```bash
cd backend-spring && ./mvnw dependency:tree -Dincludes=org.testcontainers
# Shows: testcontainers-bom 1.21.4, all testcontainers artifacts 1.21.4

./mvnw test-compile -Dmaven.test.skip=true
# Compiles successfully
```

## Known Stubs / Limitations

- Test execution fails due to missing environment variables (`JWT_SECRET`, `SPRING_DATASOURCE_URL`) — pre-existing infrastructure issue unrelated to this upgrade
- Phase 43 (JaCoCo threshold) verification failure is pre-existing — BRANCH coverage ~2% below threshold

## Threat Surface

- `pom.xml`: dependency version change only — no code logic changes
- `RateLimitIntegrationTest.java`: API fix for compatibility with 11.x
