package com.ulticode.submission.api.service;

/**
 * Minimal read seam for the submission generation fence.
 *
 * <p>Contest adjudication uses this value while holding the source submission
 * row lock; it must not reach into the submission mapper or entity module.</p>
 */
public interface SubmissionGenerationReadPort {

    /**
     * Read the current generation while serializing with rejudge/reaper writes.
     *
     * @param submissionId source submission id
     * @return current generation, or {@code null} when the submission is absent
     */
    Long findGenerationForUpdate(String submissionId);
}
