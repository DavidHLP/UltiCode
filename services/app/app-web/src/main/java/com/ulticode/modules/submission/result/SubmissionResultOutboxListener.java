package com.ulticode.modules.submission.result;

import com.ulticode.submission.api.event.SubmissionJudgedEvent;
import com.ulticode.domain.submission.enums.SubmissionStatus;
import com.ulticode.modules.submission.codec.SubmissionStatusCodec;
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
 *
 * <p><b>SPLIT-004 AC4 retirement note (cutover state):</b> this listener fires
 * on in-process {@code SubmissionJudgedEvent}s, which the regular path stops
 * publishing once the runtime cutover is active ({@code
 * app.submission.routing.mode=remote} + {@code app.submission.owner.mode=local});
 * regular verdicts are written by backend-submission. It remains active only
 * for verdicts written by the App local compatibility/rollback path. Kept as
 * a clearly labeled compatibility component;
 * do not extend it with new regular-path behavior.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SubmissionResultOutboxListener {
    private final SubmissionResultOutboxWriter outboxWriter;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onSubmissionJudged(SubmissionJudgedEvent event) {
        SubmissionStatus status = SubmissionStatusCodec.fromWire(event.getVerdict());
        if (!status.isTerminal()) {
            log.debug("Skipping non-terminal result outbox event for submission {}: {}",
                    event.getSubmissionId(), event.getVerdict());
            return;
        }

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
