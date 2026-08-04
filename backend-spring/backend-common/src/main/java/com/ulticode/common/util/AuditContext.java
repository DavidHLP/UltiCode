package com.ulticode.common.util;

import java.util.Map;
import java.io.Serializable;

/**
 * Thread-local holder for audit metadata that the method body can populate
 * before/after business logic, to be consumed by {@link com.ulticode.common.aspect.AuditAspect}.
 */
public final class AuditContext implements Serializable {

    private AuditContext() {}

    private static final ThreadLocal<Map<String, Object>> OLD_VALUES = new ThreadLocal<>();
    private static final ThreadLocal<Map<String, Object>> NEW_VALUES = new ThreadLocal<>();
    private static final ThreadLocal<String> USER_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> ENTITY_ID = new ThreadLocal<>();

    public static void setOldValues(Map<String, Object> values) {
        OLD_VALUES.set(values);
    }

    public static Map<String, Object> getOldValues() {
        return OLD_VALUES.get();
    }

    public static void setNewValues(Map<String, Object> values) {
        NEW_VALUES.set(values);
    }

    public static Map<String, Object> getNewValues() {
        return NEW_VALUES.get();
    }

    public static void setUserId(String userId) {
        USER_ID.set(userId);
    }

    public static String getUserId() {
        return USER_ID.get();
    }

    public static void setEntityId(String entityId) {
        ENTITY_ID.set(entityId);
    }

    public static String getEntityId() {
        return ENTITY_ID.get();
    }

    public static void clear() {
        OLD_VALUES.remove();
        NEW_VALUES.remove();
        USER_ID.remove();
        ENTITY_ID.remove();
    }
}
