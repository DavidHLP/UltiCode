package com.ulticode.app.api.service;

import java.io.Serializable;
import java.util.Map;

/**
 * Read seam through which the submission module reads user facts
 * without importing the user module.
 *
 * <p>The user module supplies the production adapter
 * ({@code DefaultSubmissionUserReadAdapter}) that delegates to
 * {@code UserProfileMapper} with {@code IdentityQueryService} fallback.
 *
 * <p>P7-RELOCATE-SUBMISSION-001
 */
public interface SubmissionUserReadPort {

    /**
     * Summary record carrying the four user fields the submission
     * read-side needs. Intentionally separate from
     * {@code ForumUserReadPort.UserSummary} (id/username/avatar) and
     * {@code SolutionUserReadPort.UserSummary} (id/displayName).
     */
    record UserSummary(String id, String username, String name, String avatar) implements Serializable {}

    /**
     * Check whether a user exists by id.
     *
     * @param userId the user id
     * @return {@code true} if the user exists; {@code false} otherwise
     */
    boolean existsById(String userId);

    /**
     * Resolve a single user by ID, or {@code null} if not found.
     *
     * @param userId the user id
     * @return user summary or {@code null}
     */
    UserSummary findById(String userId);

    /**
     * Batch-resolve users by ID. Returns a map of ID → {@link UserSummary}
     * for all IDs that were found; missing entries are simply absent.
     *
     * @param userIds the user ids to resolve
     * @return map of found users
     */
    Map<String, UserSummary> findAllById(Iterable<String> userIds);
}
