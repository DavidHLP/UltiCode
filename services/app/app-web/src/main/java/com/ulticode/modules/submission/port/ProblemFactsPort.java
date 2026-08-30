package com.ulticode.modules.submission.port;

/**
 * Read seam through which the submission module obtains the Problem facts its
 * write / projection / execution / notification paths need, without importing
 * {@code ProblemMapper} or {@code ProblemLanguageMapper} across the module
 * boundary.
 *
 * <p>The submission module owns this port — it declares the collaboration it
 * needs (title / slug / limits / starter code); the problem module supplies
 * the adapter ({@code ProblemFactsAdapter}). This mirrors the consumer-owned
 * hexagonal seam already used by {@link ContestSubmissionPort} and leaves the
 * write-side {@code ProblemDetailPort} (ADR-0011 satellite mutation) untouched.
 *
 * <p>Replaces the pre-2026-07-10 leakage where four submission paths
 * ({@code RemoteSubmissionWritePort}, {@code DefaultSubmissionProjection},
 * {@code CodeExecutionService}, {@code JudgedNotificationDispatcher}) each
 * reached into {@code problem.mapper.*} for title / limits / languages.
 *
 * <p><b>Non-throwing contract</b>: every method returns {@code null} for a
 * missing problem row and absorbs data-access exceptions as {@code null}, so
 * callers fall back to their defaults without try/catch — matching the
 * safe-degrade behaviour of the inline mapper reads this port replaces.
 *
 * @author ulticode
 */
public interface ProblemFactsPort {

    /**
     * Display facts (id / title / slug) for the projection and the judged
     * notification, and the existence signal for the write path.
     *
     * @param problemId the problem id
     * @return the facts, or {@code null} if the problem row is missing
     */
    ProblemDisplayFacts findDisplayFacts(Long problemId);

    /**
     * Per-problem resource limits. Individual fields may be {@code null}
     * (meaning "use the global default"); the whole result is {@code null}
     * only when the problem row is missing.
     *
     * @param problemId the problem id
     * @return the limits, or {@code null} if the problem row is missing
     */
    ProblemLimits findLimits(Long problemId);

    /**
     * The starter code registered for (problemId, language), used to infer
     * OJ parameter types for linked-list / tree problems.
     *
     * @param problemId  the problem id
     * @param language   the submission language (matched case-insensitively)
     * @return the starter code, or {@code null} if the problem / language has none
     */
    String findStarterCode(Long problemId, String language);

    /**
     * Read-only display facts. Carried as a record so the projection and
     * notification read the same shaped view the write path uses for its
     * existence check.
     */
    record ProblemDisplayFacts(Long id, String title, String slug) {
    }

    /**
     * Read-only resource limits. {@code null} fields mean "no per-problem
     * override — use the global default" (ADR-002 §8 / P2-1).
     */
    record ProblemLimits(Integer timeLimitSeconds, Integer memoryLimitMb) {
    }
}
