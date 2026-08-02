package com.ulticode.app.api.service;

import com.ulticode.domain.submission.enums.SubmissionStatus;
import java.util.List;

/**
 * Port through which the queue module's judge pipeline resolves verdicts
 * from sandbox output wire values, without importing the submission module.
 *
 * <p>P7-RELOCATE-SUBMISSION-001: extracted when VerdictResolver relocated
 * to backend-app.
 */
public interface VerdictResolvePort {

    /**
     * Reduce per-case wire values to a single canonical submission status.
     *
     * @param caseWireValues wire values from each test case
     * @return canonical submission status
     */
    SubmissionStatus reduceWire(List<String> caseWireValues);
}
