package com.ulticode.modules.contest.scoring;

import com.ulticode.modules.contest.entity.ContestParticipant;
import com.ulticode.modules.contest.entity.enums.ContestScoringMode;
import com.ulticode.modules.contest.entity.enums.ContestTieBreaker;
import com.ulticode.modules.contest.mapper.ContestParticipantMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ContestRankingComparatorTest {

    @Test
    void liveAndFinalUseTheSameTieBreakerOrder() {
        ContestParticipant finalFirst = participant("u1", 100, 10, 20, 3, 30);
        ContestParticipant finalSecond = participant("u2", 100, 10, 10, 3, 20);

        List<ContestParticipant> finalOrder = List.of(finalFirst, finalSecond).stream()
                .sorted(ContestRankingComparator.forFinal(
                        ContestScoringMode.SCORE, ContestTieBreaker.TOTAL_TIME))
                .toList();
        List<ContestParticipantMapper.ContestParticipantWithUser> liveOrder = List.of(
                live(finalFirst), live(finalSecond)).stream()
                .sorted(ContestRankingComparator.forLive(
                        ContestScoringMode.SCORE, ContestTieBreaker.TOTAL_TIME))
                .toList();

        assertThat(finalOrder).extracting(ContestParticipant::getUserId)
                .containsExactly("u2", "u1");
        assertThat(liveOrder).extracting(ContestParticipantMapper.ContestParticipantWithUser::userId)
                .containsExactly("u2", "u1");
    }

    @Test
    void icpcUsesPenaltyBeforeConfiguredTieBreaker() {
        ContestParticipant lowPenalty = participant("u1", 100, 10, 50, 5, 40);
        ContestParticipant highPenalty = participant("u2", 100, 20, 1, 1, 1);

        List<ContestParticipant> order = List.of(lowPenalty, highPenalty).stream()
                .sorted(ContestRankingComparator.forFinal(
                        ContestScoringMode.ICPC, ContestTieBreaker.TOTAL_ATTEMPTS))
                .toList();

        assertThat(order).extracting(ContestParticipant::getUserId)
                .containsExactly("u1", "u2");
    }

    private static ContestParticipant participant(
            String userId, int score, int penalty, int totalTime, int attempts, int lastSolveTime) {
        ContestParticipant participant = new ContestParticipant();
        participant.setUserId(userId);
        participant.setTotalScore(score);
        participant.setTotalPenalty(penalty);
        participant.setTotalTime(totalTime);
        participant.setAttemptCount(attempts);
        participant.setLastSolveTime(lastSolveTime);
        return participant;
    }

    private static ContestParticipantMapper.ContestParticipantWithUser live(ContestParticipant p) {
        return new ContestParticipantMapper.ContestParticipantWithUser(
                p.getId(), p.getContestId(), p.getUserId(), p.getStatus(), p.getFinalRank(),
                p.getTotalScore(), p.getTotalPenalty(), p.getTotalTime(), p.getAttemptCount(),
                LocalDateTime.MIN, LocalDateTime.MIN, p.getVirtualSessionId(), null, null, null,
                p.getLastSolveTime(), null);
    }
}
