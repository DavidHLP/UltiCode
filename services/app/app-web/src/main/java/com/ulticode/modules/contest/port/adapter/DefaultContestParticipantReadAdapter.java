package com.ulticode.modules.contest.port.adapter;

import com.ulticode.app.api.service.ContestParticipantReadPort;
import com.ulticode.modules.contest.entity.ContestParticipant;
import com.ulticode.modules.contest.mapper.ContestParticipantMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Production adapter implementing {@link ContestParticipantReadPort}.
 *
 * <p>P7-RELOCATE-CONTEST-001 AC #7.
 *
 * @author ulticode
 */
@Component
@Primary
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

    @Override
    public List<ParticipantInfo> findByContestIds(List<String> contestIds) {
        if (contestIds == null || contestIds.isEmpty()) {
            return Collections.emptyList();
        }
        return contestParticipantMapper.findByContestIds(contestIds).stream()
                .map(p -> new ParticipantInfo(p.getContestId(), p.getUserId()))
                .collect(Collectors.toList());
    }
}
