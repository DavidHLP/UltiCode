package com.ulticode.modules.solution.port;

import java.util.Collection;
import java.util.Map;

/**
 * Decoupled port for fetching user summary data needed by the solution module.
 *
 * <p>Mirrors the follow module's {@code UserReadPort} pattern: the solution
 * module defines what user data it needs, and the adapter (in backend-app)
 * provides it via UserProfileMapper + IdentityQueryService.
 *
 * <p>P7-RELOCATE-SOLUTION-001: replaces direct {@code user.entity.User} and
 * {@code user.projection.UserReadProjection} dependencies.
 */
public interface SolutionUserReadPort {

    /**
     * Find single user summary by ID.
     *
     * @param userId user ID
     * @return user summary, or {@code null} when not found
     */
    UserSummary findById(String userId);

    /**
     * Batch find user summaries by IDs.
     *
     * @param userIds collection of user IDs
     * @return map from user ID to summary; missing entries are absent
     */
    Map<String, UserSummary> findAllById(Collection<String> userIds);

    /**
     * Immutable DTO holding essential user display info.
     *
     * @param id user ID
     * @param displayName resolved display name (prefers name, falls back to username)
     * @param avatar avatar URL
     */
    record UserSummary(String id, String displayName, String avatar) {}
}
