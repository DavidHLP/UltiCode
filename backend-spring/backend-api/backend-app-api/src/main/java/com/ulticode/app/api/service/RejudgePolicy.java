package com.ulticode.app.api.service;

import com.ulticode.app.api.dto.RejudgeResult;

/**
 * Write policy that owns the ADR-003 M3b fenced rejudge state machine.
 *
 * <p>The admin module calls {@link #rejudge(String, RejudgeResult)}.
 * Flag-on delegates to fenced path; flag-off delegates to legacy path.
 */
public interface RejudgePolicy {

    /**
     * Select the rejudge strategy based on the {@code useGenerationFence}
     * feature flag and run it on the submission.
     *
     * @param submissionId  the id of the submission to rejudge; the impl resolves
     *                      the full Submission entity from storage
     * @param rejudgeResult the rejudge result payload
     * @return the rejudge result
     */
    RejudgeResult rejudge(String submissionId, RejudgeResult rejudgeResult);
}
