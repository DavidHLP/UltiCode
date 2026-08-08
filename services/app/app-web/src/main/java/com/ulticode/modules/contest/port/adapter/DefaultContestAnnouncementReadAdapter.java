package com.ulticode.modules.contest.port.adapter;

import com.ulticode.app.api.dto.ContestAnnouncementDTO;
import com.ulticode.app.api.service.ContestAnnouncementReadPort;
import com.ulticode.modules.contest.entity.ContestAnnouncement;
import com.ulticode.modules.contest.mapper.ContestAnnouncementMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Production adapter implementing {@link ContestAnnouncementReadPort}.
 *
 * <p>P7-RELOCATE-CONTEST-001 AC #7.
 *
 * @author ulticode
 */
@Component
@RequiredArgsConstructor
public class DefaultContestAnnouncementReadAdapter implements ContestAnnouncementReadPort {

    private final ContestAnnouncementMapper contestAnnouncementMapper;

    @Override
    public List<ContestAnnouncementDTO> findByContestIdOrderByCreatedAtDesc(String contestId) {
        List<ContestAnnouncement> list = contestAnnouncementMapper.findByContestIdOrderByCreatedAtDesc(contestId);
        return list.stream().map(DefaultContestAnnouncementReadAdapter::toDTO).collect(Collectors.toList());
    }

    @Override
    public ContestAnnouncementDTO findByContestIdAndId(String contestId, String announcementId) {
        ContestAnnouncement a = contestAnnouncementMapper.findByContestIdAndId(contestId, announcementId);
        return a != null ? toDTO(a) : null;
    }

    private static ContestAnnouncementDTO toDTO(ContestAnnouncement a) {
        ContestAnnouncementDTO dto = new ContestAnnouncementDTO();
        dto.setId(a.getId());
        dto.setContestId(a.getContestId());
        dto.setTitle(a.getTitle());
        dto.setContent(a.getContent());
        dto.setIsPinned(a.getIsPinned());
        dto.setCreatedAt(a.getCreatedAt());
        return dto;
    }
}
