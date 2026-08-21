package com.ulticode.submission.api.service;

import com.ulticode.submission.api.dto.SubmissionVO;

import java.util.Collection;
import java.util.List;

/**
 * Port through which external modules (contest) project submission entities
 * to VOs without importing the submission module's projection layer.
 *
 * <p>P7-RELOCATE-SUBMISSION-001: extracted when SubmissionProjection
 * relocated to the Submission owner.
 */
public interface SubmissionReadPort {

    /**
     * Convert a submission ID to a {@code SubmissionVO}.
     *
     * @param submissionId submission entity ID
     * @return submission VO, or {@code null} when not found
     */
    SubmissionVO toVO(String submissionId);

    /**
     * Convert a bounded collection of submission IDs in one owner read.
     *
     * <p>Missing IDs are omitted and returned rows preserve the input order.
     * Implementations batch owner facts instead of making one cross-owner call
     * per submission.</p>
     *
     * @param submissionIds bounded submission entity IDs; implementations may
     *                     chunk large collections internally
     * @return projected rows in input order, never null
     */
    List<SubmissionVO> toVOs(Collection<String> submissionIds);
}
