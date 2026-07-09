package com.ulticode.modules.contest.scoring;

import com.ulticode.modules.contest.entity.ContestParticipant;
import com.ulticode.modules.contest.entity.enums.ContestScoringMode;
import com.ulticode.modules.contest.mapper.ContestParticipantMapper;
import org.springframework.stereotype.Component;

import java.util.Comparator;

/**
 * IOI-mode {@link ScoringStrategy}. Per-problem score is the max over
 * submissions, so a wrong submission never costs anything; ranking is
 * by total score descending only (no penalty tie-breaker).
 */
@Component
public class IoiStrategy implements ScoringStrategy {

    @Override
    public ContestScoringMode getMode() {
        return ContestScoringMode.IOI;
    }

    @Override
    public void applyWrongSubmission(ContestParticipant participant, int penaltyPerWrong) {
    }

    @Override
    public Comparator<ContestParticipantMapper.ContestParticipantWithUser> getRankingComparator() {
        return Comparator.comparing(
                ContestParticipantMapper.ContestParticipantWithUser::totalScore,
                Comparator.nullsLast(Comparator.reverseOrder()));
    }
}
