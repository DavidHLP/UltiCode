package com.ulticode.modules.backup.port;

import java.util.Collection;
import java.util.Map;

/**
 * Port that exposes the username lookup the backup read path needs to enrich
 * the {@code createdByName} field of {@code BackupVO}, without dragging the
 * {@code BackupServiceImpl} across the module boundary into
 * {@code UserMapper}.
 *
 * <p>Replaces the direct {@code userMapper.selectBatchIds(userIds)} reach-in
 * documented in {@code /tmp/architecture-review-1783485814.html} candidate
 * 2. The user module now owns the read; the backup module consumes the port.
 *
 * <p>Adapters:
 * <ul>
 *   <li>{@code UserLookupAdapter} &mdash; production, delegates to
 *       {@code UserMapper#selectBatchIds} and reduces the {@code User}
 *       entities to a {@code Map<userId, username>}.</li>
 * </ul>
 *
 * @author ulticode
 */
public interface UserLookupPort {

    /**
     * Batch-load the usernames for the given user ids. Missing users are
     * omitted from the returned map; an empty or {@code null} input must
     * return an empty map (never {@code null}).
     *
     * @param userIds the user ids whose usernames are requested;
     *                may be empty or {@code null}
     * @return a map from user id to username; empty (never {@code null})
     *         if {@code userIds} is null or empty, or no users matched
     */
    Map<String, String> findUsernamesByIds(Collection<String> userIds);
}
