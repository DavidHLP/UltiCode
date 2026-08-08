package com.ulticode.modules.backup.port;

import java.util.Collection;
import java.util.Map;

/**
 * Port that exposes the username lookup the backup read path needs to enrich
 * the {@code createdByName} field of {@code BackupVO}, without dragging the
 * backup module across the module boundary into the auth persistence layer.
 *
 * <p>Mirrors the contract defined in {@code backend-legacy} so that
 * {@code DefaultBackupReadProjection} and its tests remain unchanged during
 * the P7 migration cutover.
 *
 * <p><strong>Non-throwing contract:</strong>
 * <ul>
 *   <li>{@code null} or empty input returns an empty map.</li>
 *   <li>Missing users are silently omitted from the result map
 *       (matches the previous inline behaviour in
 *       {@code BackupServiceImpl.toVO(Backup, Map)} which used a
 *       null-tolerant {@code userMap.get(...)} lookup).</li>
 * </ul>
 *
 * @author ulticode
 */
public interface UserLookupPort {

    /**
     * Batch-load the usernames for the given user ids.
     *
     * @param userIds the user ids whose usernames are requested;
     *                may be empty or {@code null}
     * @return a map from user id to username; empty (never {@code null})
     *         if {@code userIds} is null or empty, or no users matched
     */
    Map<String, String> findUsernamesByIds(Collection<String> userIds);
}
