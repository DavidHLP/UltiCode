package com.ulticode.modules.contest.scoring;

import com.ulticode.modules.contest.entity.enums.ContestScoringMode;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Resolves a {@link ContestScoringMode} (or a {@code Contest.scoringMode}
 * string from the DB) to its {@link ScoringStrategy}.
 *
 * <p>Spring injects every {@link ScoringStrategy} bean in the contest
 * module; this resolver builds an {@link EnumMap} at construction time
 * so a {@code O(1)} keyed lookup replaces the previous inline
 * {@code "ICPC".equalsIgnoreCase(scoringMode)} branch.
 *
 * <p>Behavioural contract:
 * <ul>
 *   <li>{@code null} or blank {@code scoringMode} string → {@link ScoreStrategy}
 *       (preserves the historical {@code null} → SCORE default used
 *       in {@code ContestScoringServiceImpl}).</li>
 *   <li>An unrecognised string (typo, deprecated mode) → {@link ScoreStrategy}
 *       and a single warn log, so the system never falls off a
 *       {@code NullPointerException} because the admin mistyped the
 *       mode.</li>
 *   <li>A valid {@link ContestScoringMode} name → the matching strategy.</li>
 * </ul>
 */
@Component
public class ScoringStrategyResolver {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ScoringStrategyResolver.class);

    private final Map<ContestScoringMode, ScoringStrategy> strategies;

    public ScoringStrategyResolver(List<ScoringStrategy> beans) {
        Map<ContestScoringMode, ScoringStrategy> map = new EnumMap<>(ContestScoringMode.class);
        for (ScoringStrategy strategy : beans) {
            map.put(strategy.getMode(), strategy);
        }
        this.strategies = Map.copyOf(map);
    }

    /**
     * Resolve a {@link ContestScoringMode} enum to its strategy. Never
     * returns {@code null} — the {@code EnumMap} is fully populated by
     * Spring component scan.
     */
    public ScoringStrategy resolve(ContestScoringMode mode) {
        return strategies.get(mode);
    }

    /**
     * Resolve a {@code Contest.scoringMode} string. Handles {@code null}
     * and blank as SCORE (the historical default), unrecognised values
     * as SCORE with a warn log, and valid mode names case-insensitively.
     */
    public ScoringStrategy resolveFromString(String scoringMode) {
        if (scoringMode == null || scoringMode.isBlank()) {
            return resolve(ContestScoringMode.SCORE);
        }
        ContestScoringMode mode;
        try {
            mode = ContestScoringMode.valueOf(scoringMode.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            log.warn("Unknown contest scoringMode '{}', falling back to SCORE strategy", scoringMode);
            return resolve(ContestScoringMode.SCORE);
        }
        return resolve(mode);
    }
}
