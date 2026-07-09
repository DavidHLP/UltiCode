package com.ulticode.modules.contest.scoring;

import com.ulticode.modules.contest.entity.ContestParticipant;
import com.ulticode.modules.contest.entity.enums.ContestScoringMode;
import com.ulticode.modules.contest.mapper.ContestParticipantMapper;
import org.springframework.stereotype.Component;

import java.util.Comparator;

/**
 * ICPC-mode {@link ScoringStrategy}. Wrong submissions add a
 * {@code penaltyPerWrong} accumulator to the participant's
 * {@code totalPenalty}; ranking is by total score (problems solved,
 * with first-solve bonus) descending, then by total penalty
 * ascending (nulls last), so a higher-penalty participant loses a
 * score-tie.
 */
@Component
public class IcpcStrategy implements ScoringStrategy {

    @Override
    public ContestScoringMode getMode() {
        return ContestScoringMode.ICPC;
    }

    @Override
    public void applyWrongSubmission(ContestParticipant participant, int penaltyPerWrong) {
        int existing = participant.getTotalPenalty() == null ? 0 : participant.getTotalPenalty();
        participant.setTotalPenalty(existing + penaltyPerWrong);
    }

    @Override
    public Comparator<ContestParticipantMapper.ContestParticipantWithUser> getRankingComparator() {
        return Comparator
                .comparing(
                        ContestParticipantMapper.ContestParticipantWithUser::totalScore,
                        Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(
                        ContestParticipantMapper.ContestParticipantWithUser::totalPenalty,
                        Comparator.nullsLast(Comparator.naturalOrder()));
    }
}
