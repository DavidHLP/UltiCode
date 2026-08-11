package com.ulticode.modules.contest.scoring;

import com.ulticode.modules.contest.entity.ContestParticipant;
import com.ulticode.modules.contest.entity.enums.ContestScoringMode;
import com.ulticode.modules.contest.entity.enums.ContestTieBreaker;
import com.ulticode.modules.contest.mapper.ContestParticipantMapper;
import org.springframework.stereotype.Component;

import java.util.Comparator;

/**
 * SCORE-mode {@link ScoringStrategy}. "AC即满分" — wrong submissions
 * are free (no penalty), and the ranking starts with total score descending and then applies
 * the configured tie-break policy.
 *
 * <p>Mirrors the historical behaviour of
 * {@code ContestScoringServiceImpl} when {@code scoringMode} was
 * {@code "SCORE"} (or {@code null}, which defaulted to SCORE).
 */
@Component
public class ScoreStrategy implements ScoringStrategy {

    @Override
    public ContestScoringMode getMode() {
        return ContestScoringMode.SCORE;
    }

    @Override
    public void applyWrongSubmission(ContestParticipant participant, int penaltyPerWrong) {
    }

    @Override
    public Comparator<ContestParticipantMapper.ContestParticipantWithUser> getRankingComparator(
            ContestTieBreaker tieBreaker) {
        return ContestRankingComparator.forLive(getMode(), tieBreaker);
    }
}
