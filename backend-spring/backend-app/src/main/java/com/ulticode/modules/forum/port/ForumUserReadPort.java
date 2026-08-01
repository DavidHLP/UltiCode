package com.ulticode.modules.forum.port;

import java.util.List;
import java.util.Map;

/**
 * Read port for forum user identity data.
 *
 * <p>Decouples the forum module from {@code com.ulticode.modules.user} by
 * abstracting the minimal user fields the forum actually needs
 * ({@code id}, {@code username}, {@code avatar}) behind a port interface.
 *
 * <p>P7-RELOCATE-FORUM-001
 *
 * @author ulticode
 */
public interface ForumUserReadPort {

    /**
     * Summary record carrying the three user fields the forum module reads.
     * The record intentionally mirrors only what forum read-side code needs;
     * it is <em>not</em> the same as {@code SolutionUserReadPort.UserSummary}
     * (which carries {@code displayName} instead of {@code username}).
     */
    record UserSummary(String id, String username, String avatar) {}

    /**
     * Resolve a single user by ID, or {@code null} if not found.
     */
    UserSummary findById(String userId);

    /**
     * Batch-resolve users by ID. Returns a map of ID → {@link UserSummary}
     * for all IDs that were found; missing entries are simply absent from the
     * map (no exception thrown).
     */
    Map<String, UserSummary> findAllById(Iterable<String> userIds);
}
