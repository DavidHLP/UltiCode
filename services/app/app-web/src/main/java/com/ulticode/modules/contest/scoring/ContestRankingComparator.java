package com.ulticode.modules.contest.scoring;

import com.ulticode.modules.contest.entity.ContestParticipant;
import com.ulticode.modules.contest.entity.enums.ContestScoringMode;
import com.ulticode.modules.contest.entity.enums.ContestTieBreaker;
import com.ulticode.modules.contest.mapper.ContestParticipantMapper;

import java.util.Comparator;

/**
 * Single source of truth for contest ranking order.
 *
 * <p>The same ordering is applied to the live read projection and to the
 * final-rank calculation. Adapters only extract ranking values from their
 * respective representations; tie semantics stay here.
 */
public final class ContestRankingComparator {

    private ContestRankingComparator() {
    }

    public static Comparator<ContestParticipantMapper.ContestParticipantWithUser> forLive(
            ContestScoringMode scoringMode, ContestTieBreaker tieBreaker) {
        Comparator<RankingValues> order = order(scoringMode, tieBreaker);
        return Comparator.comparing(ContestRankingComparator::liveValues, order);
    }

    public static Comparator<ContestParticipant> forFinal(
            ContestScoringMode scoringMode, ContestTieBreaker tieBreaker) {
        Comparator<RankingValues> order = order(scoringMode, tieBreaker);
        return Comparator.comparing(ContestRankingComparator::entityValues, order);
    }

    public static ContestScoringMode resolveScoringMode(String value) {
        if (value == null || value.isBlank()) {
            return ContestScoringMode.SCORE;
        }
        try {
            return ContestScoringMode.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return ContestScoringMode.SCORE;
        }
    }

    public static ContestTieBreaker resolveTieBreaker(String value) {
        if (value == null || value.isBlank()) {
            return ContestTieBreaker.LAST_SOLVE_TIME;
        }
        try {
            return ContestTieBreaker.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return ContestTieBreaker.LAST_SOLVE_TIME;
        }
    }

    private static Comparator<RankingValues> order(
            ContestScoringMode scoringMode, ContestTieBreaker tieBreaker) {
        ContestScoringMode mode = scoringMode == null ? ContestScoringMode.SCORE : scoringMode;
        ContestTieBreaker tie = tieBreaker == null ? ContestTieBreaker.LAST_SOLVE_TIME : tieBreaker;

        Comparator<RankingValues> comparator = Comparator.comparing(
                RankingValues::score,
                Comparator.nullsLast(Comparator.reverseOrder()));
        if (mode == ContestScoringMode.ICPC) {
            comparator = comparator.thenComparing(
                    RankingValues::penalty,
                    Comparator.nullsLast(Comparator.naturalOrder()));
        }
        comparator = switch (tie) {
            case LAST_SOLVE_TIME -> comparator.thenComparing(
                    RankingValues::lastSolveTime,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case TOTAL_TIME -> comparator.thenComparing(
                    RankingValues::totalTime,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case TOTAL_ATTEMPTS -> comparator.thenComparing(
                    RankingValues::attempts,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case NONE -> comparator;
        };
        return comparator.thenComparing(
                RankingValues::userId,
                Comparator.nullsLast(Comparator.naturalOrder()));
    }

    private static RankingValues liveValues(ContestParticipantMapper.ContestParticipantWithUser participant) {
        return new RankingValues(
                participant.totalScore(),
                participant.totalPenalty(),
                participant.totalTime(),
                participant.attemptCount(),
                participant.lastSolveTime(),
                participant.userId());
    }

    private static RankingValues entityValues(ContestParticipant participant) {
        return new RankingValues(
                participant.getTotalScore(),
                participant.getTotalPenalty(),
                participant.getTotalTime(),
                participant.getAttemptCount(),
                participant.getLastSolveTime(),
                participant.getUserId());
    }

    private record RankingValues(
            Integer score,
            Integer penalty,
            Integer totalTime,
            Integer attempts,
            Integer lastSolveTime,
            String userId) {
    }
}
