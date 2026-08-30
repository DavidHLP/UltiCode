package com.ulticode.submission.api.service;

/**
 * Minimal read seam for the submission generation fence.
 *
 * <p>Contest adjudication uses this value as a stale-event guard; the method
 * does not hold a database lock across a Dubbo call. It must not reach into
 * the submission mapper or entity module.</p>
 */
public interface SubmissionGenerationReadPort {

    /**
     * Read the current generation for stale-event validation. The owner-side
     * terminal verdict CAS and event outbox establish the event generation;
     * this read is deliberately not a cross-service transaction lock.
     *
     * @param submissionId source submission id
     * @return current generation, or {@code null} when the submission is absent
     */
    Long findGenerationForUpdate(String submissionId);
}
