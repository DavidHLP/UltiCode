package com.ulticode.app.api.service;

import java.io.Serializable;
import java.util.Collection;
import java.util.Map;

/**
 * Read seam through which the submission module obtains Problem facts
 * (title / slug / limits / starter code) without importing problem mappers.
 *
 * <p>Non-throwing contract: every method returns {@code null} for a missing
 * problem row, matching the safe-degrade behaviour of the inline mapper reads
 * this port replaces.
 */
public interface ProblemFactsPort {

    /**
     * Display facts (id / title / slug).
     *
     * @param problemId the problem id
     * @return the facts, or {@code null} if the problem row is missing
     */
    ProblemDisplayFacts findDisplayFacts(Long problemId);

    /**
     * Bulk display facts keyed by problem id.
     *
     * <p>SPLIT-004 slice-8: list/detail read routing needs problem title/slug
     * for a page of submissions; a single batched read avoids the N+1
     * pattern of calling {@link #findDisplayFacts(Long)} per row. The map
     * contains only ids that resolve to a problem row (missing ids are
     * simply absent). Implementations must not throw for unknown ids.
     *
     * @param problemIds problem ids to resolve (may be empty)
     * @return display facts keyed by problem id, never {@code null}
     */
    Map<Long, ProblemDisplayFacts> findDisplayFactsBatch(Collection<Long> problemIds);

    /**
     * Per-problem resource limits. Individual fields may be {@code null}
     * (meaning "use the global default").
     *
     * @param problemId the problem id
     * @return the limits, or {@code null} if the problem row is missing
     */
    ProblemLimits findLimits(Long problemId);

    /**
     * The starter code registered for (problemId, language).
     *
     * @param problemId  the problem id
     * @param language   the submission language (matched case-insensitively)
     * @return the starter code, or {@code null}
     */
    String findStarterCode(Long problemId, String language);

    record ProblemDisplayFacts(Long id, String title, String slug) implements Serializable {

        private static final long serialVersionUID = 1L;
}

    record ProblemLimits(Integer timeLimitSeconds, Integer memoryLimitMb) implements Serializable {

        private static final long serialVersionUID = 1L;
}

    /**
     * Contest-facing facts (title / slug / difficulty / acceptanceRate).
     *
     * @param problemId the problem id
     * @return the facts, or {@code null} if the problem row is missing
     */
    ContestProblemFacts findContestProblemFacts(Long problemId);

    record ContestProblemFacts(Long id, String title, String slug, String difficulty,
                               java.math.BigDecimal acceptanceRate) implements Serializable {

        private static final long serialVersionUID = 1L;
}
}
