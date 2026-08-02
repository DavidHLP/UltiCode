package com.ulticode.app.api.service;

import com.ulticode.app.api.dto.ContestAnnouncementDTO;

import java.util.List;

/**
 * Entity-free contest announcement read port consumed by backend-admin.
 *
 * <p>P7-RELOCATE-CONTEST-001 AC #7.
 *
 * @author ulticode
 */
public interface ContestAnnouncementReadPort {

    /**
     * List announcements for a contest, ordered by pinned-then-recency.
     */
    List<ContestAnnouncementDTO> findByContestIdOrderByCreatedAtDesc(String contestId);

    /**
     * Fetch a single announcement by contest + announcement id.
     */
    ContestAnnouncementDTO findByContestIdAndId(String contestId, String announcementId);
}
