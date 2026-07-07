package com.ulticode.modules.queue.pipeline;

import com.ulticode.modules.submission.entity.Submission;
import java.util.List;

/**
 * Immutable result of the judge execution pipeline.
 *
 * Carries the verdict, peak runtime/memory metrics, and per-case details
 * from the sandbox run. The worker uses this to persist the verdict and
 * push the WebSocket notification.
 *
 * @param verdict          wire-string verdict (e.g. "Accepted", "Wrong Answer")
 * @param maxRuntimeMs     peak runtime across all cases, in milliseconds
 * @param maxMemoryMb      peak memory across all cases, in megabytes
 * @param testCaseDetails  per-case detail list (caseId + caseScope populated
 *                         when sourced from the canonical test_cases table)
 */
public record JudgeExecutionResult(
        String verdict,
        int maxRuntimeMs,
        double maxMemoryMb,
        List<Submission.TestCaseDetail> testCaseDetails
) {
}
