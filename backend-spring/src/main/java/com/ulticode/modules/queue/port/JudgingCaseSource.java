package com.ulticode.modules.queue.port;

import java.util.List;

/**
 * Queue-owned source of judge-ready cases for a problem.
 *
 * <p>The judge execution pipeline depends on this seam alone; it never
 * reaches into the Problem module's mappers, entities, or canonical-vs-legacy
 * selection. Adapters live on the queue side (the queue already depends on
 * problem) and a composite selects between them via configuration, so the
 * source policy concentrates in one place instead of branching inside the
 * pipeline.
 */
public interface JudgingCaseSource {

    /**
     * Load the judge-ready cases for a problem.
     *
     * @param problemId the problem ID (numeric)
     * @return the cases in run order; empty if no eligible cases exist (the
     *         pipeline treats empty as fail-closed / System Error)
     */
    List<JudgingCase> loadCases(long problemId);
}
