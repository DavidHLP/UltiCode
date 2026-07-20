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
 * Delegation seam for {@code ContestParticipant} status transitions called by
 * the scheduled lifecycle path ({@code ContestLifecycleServiceImpl}).
 *
 * <p>This seam gives the lifecycle service a single mockable collaborator
 * (instead of wiring the mapper directly into the scheduler) and owns three
 * practical concerns: the REGISTERED→STARTED and STARTED→FINISHED(real)
 * bulk calls are forwarded to {@link ContestParticipantMapper} with the
 * canonical status literals; the virtual batch call adds input hygiene
 * (null/empty guard + {@link HashSet} dedup) before dispatch.
 *
 * <p>The transition rules themselves — conditional-UPDATE WHERE guards,
 * status literal checks, and audit-stamp columns — live in the mapper's
 * {@code @Update} SQL, not here. This class deliberately does not re-express
 * them so there is one source of truth for the guards.
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
