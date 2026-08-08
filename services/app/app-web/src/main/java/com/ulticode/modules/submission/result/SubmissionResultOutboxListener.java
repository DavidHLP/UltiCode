package com.ulticode.modules.submission.result;

import com.ulticode.app.api.event.SubmissionJudgedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Writes the result outbox row in the same transaction as the verdict (P6-RESULT-001).
 *
 * <p>Uses {@code BEFORE_COMMIT} so the outbox insert joins the verdict write transaction.
 * If the transaction rolls back, both the verdict and the outbox row are discarded.
 * If the transaction commits, the outbox row survives JVM crash and the
 * {@link SubmissionResultDispatcher} will publish it to the integration bus.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SubmissionResultOutboxListener {

    private final SubmissionResultOutboxWriter outboxWriter;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onSubmissionJudged(SubmissionJudgedEvent event) {
        outboxWriter.recordVerdictResult(
                event.getSubmissionId(),
                event.getGeneration() > 0 ? event.getGeneration() : 1L,
                event.getUserId(),
                String.valueOf(event.getProblemId()),
                event.getVerdict(),
                event.getRuntimeMs(),
                event.getMemoryMb(),
                event.getContestId());
        log.debug("Result outbox row written for submission {} gen {}",
                event.getSubmissionId(), event.getGeneration());
    }
}
