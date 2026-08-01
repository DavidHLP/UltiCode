package com.ulticode.modules.follow.port;

import java.util.Collection;
import java.util.Map;

/**
 * Decoupled port for fetching user summary data needed by follow module.
 */
public interface UserReadPort {
    /**
     * Check if user exists.
     */
    boolean exists(String userId);

    /**
     * Find single user summary by ID.
     */
    UserSummaryData findById(String userId);

    /**
     * Batch find user summaries by IDs.
     */
    Map<String, UserSummaryData> findByIds(Collection<String> userIds);

    /**
     * Immutable DTO holding essential user display info.
     */
    record UserSummaryData(String id, String username, String avatar, String bio) {}
}
