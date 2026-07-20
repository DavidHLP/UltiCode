package com.ulticode.modules.contest.service.impl;

import com.ulticode.modules.contest.mapper.ContestParticipantMapper;
import com.ulticode.modules.contest.service.ContestParticipantTransitions;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Single implementation of all {@code ContestParticipant} status transitions.
 *
 * <p>Both the interactive path ({@code ContestParticipationServiceImpl}) and
 * the scheduled path ({@code ContestLifecycleServiceImpl}) delegate here so
 * that transition rules, conditional-UPDATE guards, and audit stamping have one
 * locality.
 */
@Service
@RequiredArgsConstructor
public class ContestParticipantTransitionsImpl implements ContestParticipantTransitions {

    private final ContestParticipantMapper participantMapper;


    @Override
    @Transactional
    public int batchStartParticipants(String contestId, LocalDateTime now) {
        return participantMapper.batchUpdateStatus(contestId, "REGISTERED", "STARTED", now);
    }

    @Override
    @Transactional
    public int finishStartedRealParticipants(String contestId, LocalDateTime now) {
        // Real participants: is_virtual = 0.
        // We do NOT clear virtual_session_id because real participants never have one.
        return participantMapper.finishStartedRealParticipants(contestId, now);
    }

    @Override
    @Transactional
    public int bulkFinishVirtualByIds(Collection<String> participantIds, LocalDateTime now) {
        if (participantIds == null || participantIds.isEmpty()) {
            return 0;
        }
        Set<String> ids = new HashSet<>(participantIds);
        return participantMapper.bulkFinishByIds(ids, now);
    }
}
