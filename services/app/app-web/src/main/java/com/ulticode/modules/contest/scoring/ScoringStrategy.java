package com.ulticode.modules.contest.scoring;

import com.ulticode.modules.contest.entity.ContestParticipant;
import com.ulticode.modules.contest.entity.enums.ContestScoringMode;
import com.ulticode.modules.contest.entity.enums.ContestTieBreaker;
import com.ulticode.modules.contest.mapper.ContestParticipantMapper;

import java.util.Comparator;

/**
 * Runtime owner of per-mode contest scoring semantics. The
 * {@link ContestScoringMode} enum is the key; the strategy is the
 * implementation that knows what to do for a given mode.
 *
 * <p>This seam was extracted from {@code ContestScoringServiceImpl} which
 * previously had one {@code if ("ICPC".equalsIgnoreCase(scoringMode))}
 * branch while the {@code SCORE} and {@code IOI} modes were silent
 * fall-throughs. The {@code ScoringRule} entity in the admin module
 * (table {@code contest_scoring_rules}) is admin-side configuration and
 * is intentionally NOT consulted at runtime — the rule shape that
 * actually drives the scoring path lives here.
 *
 * <p>Every public method has well-defined semantics per mode:
 * <ul>
 *   <li>{@link #applyWrongSubmission(ContestParticipant, int)} — only
 *       ICPC adds a wrong-submission penalty; SCORE and IOI are no-ops.</li>
 *   <li>{@link #getRankingComparator(ContestTieBreaker)} — mode-aware sort for
 *       live ranking reads with the contest tie-break policy.</li>
 *   <li>{@link #getMode()} — the enum key the resolver used to look up
 *       this strategy.</li>
 * </ul>
 *
 * <p>Implementations are pure (no Spring state, no I/O). The
 * {@link ScoringStrategyResolver} maps a {@code Contest.scoringMode}
 * string to one of three concrete strategies; the
 * {@code ContestScoringServiceImpl} and {@code RankingServiceImpl}
 * delegate to the resolver instead of branching inline.
 *
 * @author ulticode
 */
public interface ScoringStrategy {

    /**
     * @return the {@link ContestScoringMode} this strategy implements;
     *         used by the resolver for sanity-checking the keyed lookup
     */
    ContestScoringMode getMode();

    /**
     * Apply a wrong-submission penalty to the participant in place.
     * Implementations may mutate the supplied entity.
     *
     * @param participant     the participant being scored (non-null)
     * @param penaltyPerWrong configured penalty, in seconds, per wrong
     *                        submission (already resolved to a non-null
     *                        value by the caller; default 20 in
     *                        {@code ContestScoringServiceImpl})
     */
    void applyWrongSubmission(ContestParticipant participant, int penaltyPerWrong);

    /**
     * @param tieBreaker the contest tie-break policy
     * @return the comparator used to rank live participants of this mode
     */
    Comparator<ContestParticipantMapper.ContestParticipantWithUser> getRankingComparator(
            ContestTieBreaker tieBreaker);
}
