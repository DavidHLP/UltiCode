package com.ulticode.modules.contest.service;

import com.ulticode.modules.contest.dto.ContestVO;
import com.ulticode.modules.contest.dto.ParticipationStatusDTO;

import java.util.List;

/**
 * Deep contest participation lifecycle seam.
 *
 * <p>Single owner of the (contest, user) join state machine: registration
 * invariants, virtual replay start/finish, participation status reads, and
 * the user's contest history. Every method that mutates or reads the
 * participation row flows through here so the invariants concentrate in one
 * module instead of being mirrored as pass-throughs on the contest write
 * facade.
 *
 * <p>The registration side effect (contest participation achievement) is
 * fired from within {@link #registerForContest}; callers never need to know
 * it exists.
 */
public interface ContestParticipationService {

    void registerForContest(String contestId, String userId);

    void unregisterFromContest(String contestId, String userId);

    ParticipationStatusDTO getParticipationStatus(String contestId, String userId);

    List<ContestVO> getUserContests(String userId, String type);

    ParticipationStatusDTO startVirtualContest(String contestId, String userId);

    ParticipationStatusDTO getVirtualSession(String contestId, String userId);

    void finishVirtualContest(String contestId, String sessionId, String userId);
}
