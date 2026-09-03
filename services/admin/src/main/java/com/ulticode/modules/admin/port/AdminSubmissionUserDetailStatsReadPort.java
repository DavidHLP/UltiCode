package com.ulticode.modules.admin.port;

import com.ulticode.submission.api.dto.SubmissionUserDetailStatsSnapshotDTO;

/**
 * Admin-owned read seam for the Submission user's detail statistics snapshot.
 *
 * <p>The Submission owner supplies submission count, accepted-problem count,
 * and streak in one provider-owned query. A provider failure is surfaced by
 * the adapter as an exception; it is never represented as a zero-valued
 * snapshot.
 */
public interface AdminSubmissionUserDetailStatsReadPort {

    /**
     * Load the authoritative Submission detail stats for one user.
     *
     * @param userId user identifier
     * @return non-null snapshot when the owner answered successfully
     */
    SubmissionUserDetailStatsSnapshotDTO loadUserDetailStats(String userId);
}
