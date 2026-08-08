package com.ulticode.app.api.service;

import com.ulticode.app.api.dto.SubmissionVO;

/**
 * Port through which external modules (contest) project submission entities
 * to VOs without importing the submission module's projection layer.
 *
 * <p>P7-RELOCATE-SUBMISSION-001: extracted when SubmissionProjection
 * relocated to backend-app.
 */
public interface SubmissionReadPort {

    /**
     * Convert a submission ID to a {@code SubmissionVO}.
     *
     * @param submissionId submission entity ID
     * @return submission VO, or {@code null} when not found
     */
    SubmissionVO toVO(String submissionId);
}
