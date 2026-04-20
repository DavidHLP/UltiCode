package com.ulticode.modules.contest.service;

import com.ulticode.modules.contest.dto.ContestVO;
import com.ulticode.modules.contest.dto.ParticipationStatusDTO;

import java.util.List;
import java.util.function.BiFunction;

/**
 * Service for contest scheduling and lifecycle operations.
 * Handles registration, virtual contests, and participation tracking.
 */
public interface ContestSchedulerService {

    void registerForContest(String contestId, String userId);

    void unregisterFromContest(String contestId, String userId);

    ParticipationStatusDTO getParticipationStatus(String contestId, String userId);

    List<ContestVO> getUserContests(String userId, String type);

    ParticipationStatusDTO startVirtualContest(String contestId, String userId);

    ParticipationStatusDTO getVirtualSession(String contestId, String userId);

    void finishVirtualContest(String contestId, String sessionId, String userId);
}
