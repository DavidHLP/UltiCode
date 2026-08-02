package com.ulticode.app.api.service;

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

    record ProblemDisplayFacts(Long id, String title, String slug) {}

    record ProblemLimits(Integer timeLimitSeconds, Integer memoryLimitMb) {}
}
