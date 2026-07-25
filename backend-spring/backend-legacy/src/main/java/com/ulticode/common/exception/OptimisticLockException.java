package com.ulticode.common.exception;

import lombok.Getter;

/**
 * Exception thrown when optimistic locking fails due to version conflict.
 * Used with MyBatis-Plus @Version annotation.
 */
@Getter
public class OptimisticLockException extends RuntimeException {

    private final Long currentVersion;

    public OptimisticLockException(Long currentVersion) {
        super("Version conflict: expected version does not match current version");
        this.currentVersion = currentVersion;
    }

    public OptimisticLockException(Long currentVersion, String message) {
        super(message);
        this.currentVersion = currentVersion;
    }
}