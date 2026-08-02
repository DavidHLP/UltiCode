package com.ulticode.modules.admin.service;

import com.ulticode.app.api.dto.ContestAnnouncementDTO;
import com.ulticode.modules.admin.dto.AdminContestVO;
import com.ulticode.modules.contest.dto.CreateContestDTO;
import com.ulticode.modules.contest.dto.UpdateContestDTO;

/**
 * Deep write module for the admin contest surface.
 *
 * <p>P7-RELOCATE-CONTEST-001 AC #7: ContestAnnouncement entity return type
 * replaced with app-api ContestAnnouncementDTO. CreateContestDTO/UpdateContestDTO
 * stay (domain-module DTOs, not entities or mappers).
 *
 * @author ulticode
 */
public interface AdminContestMutationService {

    AdminContestVO createContest(CreateContestDTO dto, String userId);

    AdminContestVO updateContest(String id, UpdateContestDTO dto);

    void deleteContest(String id);

    AdminContestVO startContest(String id);

    AdminContestVO endContest(String id);

    ContestAnnouncementDTO createAnnouncement(String contestId, String title, String content, Boolean isPinned);

    ContestAnnouncementDTO updateAnnouncement(String contestId, String announcementId, String title, String content, Boolean isPinned);

    void deleteAnnouncement(String contestId, String announcementId);
}
