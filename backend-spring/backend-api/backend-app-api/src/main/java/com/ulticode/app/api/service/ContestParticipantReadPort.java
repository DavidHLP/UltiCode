package com.ulticode.app.api.service;

import java.util.List;

/**
 * Entity-free contest participant analytics port consumed by
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
}
