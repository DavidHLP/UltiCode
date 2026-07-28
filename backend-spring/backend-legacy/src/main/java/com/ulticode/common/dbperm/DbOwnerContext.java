package com.ulticode.common.dbperm;

/**
 * Thread-local context holder for the current execution thread's DB Owner context (P3-DBPERM-001).
 */
public final class DbOwnerContext {

    private DbOwnerContext() {}

    private static final ThreadLocal<TableOwner> CURRENT_OWNER = new ThreadLocal<>();

    public static void setOwner(TableOwner owner) {
        CURRENT_OWNER.set(owner);
    }

    public static TableOwner getOwner() {
        return CURRENT_OWNER.get();
    }

    public static void clear() {
        CURRENT_OWNER.remove();
    }
}
