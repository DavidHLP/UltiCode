package com.ulticode.modules.contest.entity.enums;

/**
 * Contest status enumeration
 */
public enum ContestStatus {
    DRAFT,
    UPCOMING,
    RUNNING,
    /**
     * Terminal side effects are being finalized; retries must resume this state.
     */
    FINISHING,
    FINISHED,
    CANCELLED
}
