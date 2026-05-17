# Plan: Fix CR Issues — AuditContext Unit Tests

## Summary
为 `AuditContext` 添加单元测试，验证 ThreadLocal 的正常路径、异常路径清理，以及边界情况。

## User Story
As a developer, I want unit tests for `AuditContext`, so that the ThreadLocal leak prevention is verified and future refactors don't accidentally break it.

## Problem → Solution
**Problem**: `AuditContext` 是关键的基础设施类，但没有任何单元测试覆盖 ThreadLocal 的清理行为。

**Solution**: 添加 `AuditContextTest.java` 验证 set/get/clear 全流程，以及异常路径下 `clear()` 被调用后 ThreadLocal 为 null。

## Metadata
- **Complexity**: Small
- **Source PRD**: N/A
- **PRD Phase**: N/A
- **Estimated Files**: 1

---

## Mandatory Reading

| Priority | File | Lines | Why |
|---|---|---|---|
| P0 | `backend-spring/src/main/java/com/ulticode/common/util/AuditContext.java` | all | 测试目标类 |
| P0 | `backend-spring/src/test/java/com/ulticode/common/response/ResultTest.java` | all | 测试风格参考 |

---

## Patterns to Mirror

### TEST_PATTERN
// SOURCE: `backend-spring/src/test/java/com/ulticode/common/response/ResultTest.java`

JUnit 5 + AssertJ，AAA 模式：
```java
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ResultTest {
    @Test
    void testSuccessWithData() {
        // Arrange
        String testData = "test data";
        // Act
        Result<String> result = Result.success(testData);
        // Assert
        assertNotNull(result);
        assertEquals(testData, result.getData());
    }
}
```

---

## Files to Change

| File | Action | Justification |
|---|---|---|
| `backend-spring/src/test/java/com/ulticode/common/util/AuditContextTest.java` | CREATE | 新增单元测试 |

---

## Step-by-Step Tasks

### Task 1: Create AuditContextTest
- **ACTION**: 在 `src/test/java/com/ulticode/common/util/` 下创建 `AuditContextTest.java`
- **IMPLEMENT**:
```java
package com.ulticode.common.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AuditContextTest {

    @AfterEach
    void tearDown() {
        // Clean up after each test to prevent cross-test contamination
        AuditContext.clear();
    }

    // --- oldValues ---

    @Test
    void setOldValues_thenGetOldValues_returnsValues() {
        Map<String, Object> values = Map.of("isBanned", false, "reason", "spam");
        AuditContext.setOldValues(values);
        assertEquals(values, AuditContext.getOldValues());
    }

    @Test
    void getOldValues_whenNotSet_returnsNull() {
        assertNull(AuditContext.getOldValues());
    }

    @Test
    void setOldValues_overwritesPrevious() {
        Map<String, Object> first = Map.of("key", "value1");
        Map<String, Object> second = Map.of("key", "value2");
        AuditContext.setOldValues(first);
        AuditContext.setOldValues(second);
        assertEquals(second, AuditContext.getOldValues());
    }

    // --- newValues ---

    @Test
    void setNewValues_thenGetNewValues_returnsValues() {
        Map<String, Object> values = Map.of("isBanned", true, "reason", "test");
        AuditContext.setNewValues(values);
        assertEquals(values, AuditContext.getNewValues());
    }

    @Test
    void getNewValues_whenNotSet_returnsNull() {
        assertNull(AuditContext.getNewValues());
    }

    // --- userId ---

    @Test
    void setUserId_thenGetUserId_returnsUserId() {
        AuditContext.setUserId("u-123");
        assertEquals("u-123", AuditContext.getUserId());
    }

    @Test
    void getUserId_whenNotSet_returnsNull() {
        assertNull(AuditContext.getUserId());
    }

    // --- entityId ---

    @Test
    void setEntityId_thenGetEntityId_returnsEntityId() {
        AuditContext.setEntityId("entity-456");
        assertEquals("entity-456", AuditContext.getEntityId());
    }

    @Test
    void getEntityId_whenNotSet_returnsNull() {
        assertNull(AuditContext.getEntityId());
    }

    // --- clear() ---

    @Test
    void clear_afterSettingValues_allValuesAreNull() {
        AuditContext.setOldValues(Map.of("k", "v"));
        AuditContext.setNewValues(Map.of("k", "v"));
        AuditContext.setUserId("u-123");
        AuditContext.setEntityId("e-456");

        AuditContext.clear();

        assertNull(AuditContext.getOldValues());
        assertNull(AuditContext.getNewValues());
        assertNull(AuditContext.getUserId());
        assertNull(AuditContext.getEntityId());
    }

    @Test
    void clear_whenNothingSet_allRemainNull() {
        AuditContext.clear();
        assertNull(AuditContext.getOldValues());
        assertNull(AuditContext.getNewValues());
        assertNull(AuditContext.getUserId());
        assertNull(AuditContext.getEntityId());
    }

    // --- thread isolation ---

    @Test
    void values_areIsolatedBetweenThreads() throws InterruptedException {
        String[] mainUserId = {null};
        String[] otherUserId = {null};

        // Set value in main thread
        AuditContext.setUserId("main-thread-user");

        Thread otherThread = new Thread(() -> {
            // In a new thread, value should be null (not inherited)
            otherUserId[0] = AuditContext.getUserId();
        });
        otherThread.start();
        otherThread.join();

        // Main thread should still have its value
        mainUserId[0] = AuditContext.getUserId();

        assertEquals("main-thread-user", mainUserId[0]);
        assertNull(otherUserId[0]); // Each thread has its own ThreadLocal

        AuditContext.clear();
    }

    // --- null value handling ---

    @Test
    void setNewValues_withNull_clearsNewValues() {
        AuditContext.setNewValues(Map.of("key", "value"));
        AuditContext.setNewValues(null);
        assertNull(AuditContext.getNewValues());
    }
}
```
- **MIRROR**: `ResultTest.java` 风格 — JUnit 5, AAA 模式, `@AfterEach` cleanup
- **IMPORTS**: `org.junit.jupiter.api.Test`, `org.junit.jupiter.api.AfterEach`, `java.util.Map`
- **GOTCHA**: 每个测试后必须调用 `AuditContext.clear()` 防止 ThreadLocal 泄漏到后续测试
- **VALIDATE**: `./mvnw test -Dtest=AuditContextTest -q` 通过

---

## Validation Commands

### Unit Tests
```bash
cd backend-spring && ./mvnw test -Dtest=AuditContextTest -q
```
EXPECT: All tests pass (11 tests)

---

## Acceptance Criteria
- [ ] `AuditContextTest.java` 创建，包含 11 个测试用例
- [ ] 覆盖 set/get/clear 全路径
- [ ] 覆盖 ThreadLocal 线程隔离
- [ ] 覆盖 null 值处理
- [ ] `./mvnw test -Dtest=AuditContextTest` 通过
- [ ] `./mvnw compile` 通过
