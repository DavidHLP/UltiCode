package com.ulticode.modules.submission.result;

import com.ulticode.common.uuid.UuidGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transactional writer for the {@code submission_result_outbox} table (P6-RESULT-001).
 *
 * <p>Separated from {@link SubmissionResultDispatcher} to avoid coupling the verdict
 * write path to the scheduler. Uses {@code MANDATORY} propagation so the outbox
 * insert always joins the caller's transaction (the verdict write).
 *
 * <p>Uses {@code (submission_id, generation)} as idempotency key — each rejudge
 * generation creates a new immutable row, preserving event history.
 */
@Component
@RequiredArgsConstructor
public class SubmissionResultOutboxWriter {

    private final SubmissionResultOutboxMapper resultMapper;
    private final UuidGenerator uuidGenerator;

    /**
     * Record a result event in the outbox within the caller's transaction.
     *
     * @param generation fence generation (1 for first attempt, matching Submission default)
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordVerdictResult(String submissionId, long generation, String userId, String problemId,
                                     String verdict, int runtimeMs, double memoryMb,
                                     String contestId) {
        resultMapper.insertIfAbsent(
                uuidGenerator.newId(),
                submissionId, generation, userId, problemId, verdict,
                runtimeMs, memoryMb, contestId);
    }
}
