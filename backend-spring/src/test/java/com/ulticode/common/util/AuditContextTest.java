package com.ulticode.common.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for AuditContext ThreadLocal behavior.
 */
class AuditContextTest {

    @AfterEach
    void tearDown() {
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

        AuditContext.setUserId("main-thread-user");

        Thread otherThread = new Thread(() -> {
            otherUserId[0] = AuditContext.getUserId();
        });
        otherThread.start();
        otherThread.join();

        mainUserId[0] = AuditContext.getUserId();

        assertEquals("main-thread-user", mainUserId[0]);
        assertNull(otherUserId[0]);

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
