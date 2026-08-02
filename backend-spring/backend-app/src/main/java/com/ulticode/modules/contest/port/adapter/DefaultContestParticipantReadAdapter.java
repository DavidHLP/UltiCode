package com.ulticode.modules.contest.port.adapter;

import com.ulticode.app.api.service.ContestParticipantReadPort;
import com.ulticode.modules.contest.mapper.ContestParticipantMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Production adapter implementing {@link ContestParticipantReadPort}.
 *
 * <p>P7-RELOCATE-CONTEST-001 AC #7.
 *
 * @author ulticode
 */
@Component
@RequiredArgsConstructor
public class DefaultContestParticipantReadAdapter implements ContestParticipantReadPort {

    private final ContestParticipantMapper contestParticipantMapper;

    @Override
    public long countByContestIds(List<String> contestIds) {
        if (contestIds == null || contestIds.isEmpty()) {
            return 0;
        }
        return contestParticipantMapper.findByContestIds(contestIds).size();
    }
}
