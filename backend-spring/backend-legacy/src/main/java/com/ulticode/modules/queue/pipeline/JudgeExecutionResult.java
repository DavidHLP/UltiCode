package com.ulticode.modules.queue.pipeline;

import com.ulticode.modules.submission.entity.Submission;
import com.ulticode.domain.submission.enums.SubmissionStatus;

import java.util.List;

/**
 * Immutable result of the judge execution pipeline.
 *
 * Carries the typed verdict, peak runtime/memory metrics, and per-case details
 * from the sandbox run. The worker uses this to persist the verdict and push
 * the WebSocket notification. The verdict is typed (not a wire string) so the
 * write-port seam stays typed end-to-end; callers that need the wire form
 * (e.g. the push payload) call {@link SubmissionStatus#wireValue()}.
 *
 * @param status           typed verdict (never null)
 * @param maxRuntimeMs     peak runtime across all cases, in milliseconds
 * @param maxMemoryMb      peak memory across all cases, in megabytes
 * @param testCaseDetails  per-case detail list (caseId + caseScope populated
 *                         when sourced from the canonical test_cases table)
 */
public record JudgeExecutionResult(
        SubmissionStatus status,
        int maxRuntimeMs,
        double maxMemoryMb,
        List<Submission.TestCaseDetail> testCaseDetails
) {
}
