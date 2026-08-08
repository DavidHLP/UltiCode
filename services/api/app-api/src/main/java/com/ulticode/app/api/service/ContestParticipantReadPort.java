package com.ulticode.app.api.service;

import java.util.List;

/**
 * Entity-free contest participant read port consumed by
 * {@code DefaultAdminAnalyticsPortAdapter} after contest relocation.
 *
 * <p>P7-RELOCATE-CONTEST-001 AC #7.
 *
 * @author ulticode
 */
public interface ContestParticipantReadPort {

    /**
     * Count participants across the given contest ids.
     *
     * @param contestIds contest ids (null/empty = 0)
     * @return total participant rows
     */
    long countByContestIds(List<String> contestIds);

    /**
     * Lightweight participant record for analytics.
     */
    record ParticipantInfo(String contestId, String userId) {}

    /**
     * Find participants for the given contest ids.
     *
     * @param contestIds contest ids (null/empty = empty list)
     * @return participant info list
     */
    List<ParticipantInfo> findByContestIds(List<String> contestIds);
}
