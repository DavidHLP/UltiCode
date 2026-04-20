# Phase 21: Security Hardening - Plan 01 Summary

**Committed:** 99df9c028
**Date:** 2026-04-20

## Changes Made

### FRAG-03: MonitoringServiceImpl — volatile long → AtomicLong

**File:** `backend-spring/src/main/java/com/ulticode/modules/monitoring/service/impl/MonitoringServiceImpl.java`

- Import: `java.util.concurrent.atomic.AtomicLong`
- Field: `private volatile long queryCount = 0` → `private final AtomicLong queryCount = new AtomicLong(0)`
- Increment: `queryCount++` → `queryCount.incrementAndGet()`
- Builder: `queryCount` → `queryCount.get()`

**Verification:**
```bash
grep "AtomicLong" backend-spring/src/main/java/com/ulticode/modules/monitoring/service/impl/MonitoringServiceImpl.java
grep "incrementAndGet" backend-spring/src/main/java/com/ulticode/modules/monitoring/service/impl/MonitoringServiceImpl.java
```

### SEC-02: AdminForumServiceImpl — commentCount sort with real data

**File:** `backend-spring/src/main/java/com/ulticode/modules/admin/service/impl/AdminForumServiceImpl.java`

Added in-memory sort after enrich step:
```java
if ("commentCount".equals(sortBy)) {
    final boolean asc = isAsc;
    vos.sort((a, b) -> {
        int countA = a.getCommentCount() != null ? a.getCommentCount() : 0;
        int countB = b.getCommentCount() != null ? b.getCommentCount() : 0;
        return asc ? Integer.compare(countA, countB) : Integer.compare(countB, countA);
    });
}
```

## Self-Check

| Requirement | Status |
|-------------|--------|
| SEC-01 System.out | NO-CHANGE (false positive — sandbox generated code) |
| SEC-02 commentCount sort | ✅ IMPLEMENTED |
| SEC-03 springdoc | NO-CHANGE (already correct 2.6.0) |
| SEC-04 CI Flyway URL | NO-CHANGE (already correct Maven Central) |
| FRAG-01 JWT nulls | NO-CHANGE (already handled) |
| FRAG-02 Redis nulls | NO-CHANGE (degraded response pattern) |
| FRAG-03 AtomicLong | ✅ IMPLEMENTED |

**PASSED** — Phase 21 complete. 5 requirements were already correct (false positives or prior fixes). Only 2 required code changes.
