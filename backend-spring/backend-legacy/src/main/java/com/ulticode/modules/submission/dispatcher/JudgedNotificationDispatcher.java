package com.ulticode.modules.submission.dispatcher;

import com.ulticode.modules.notification.dispatcher.NotificationDispatcher;
import com.ulticode.modules.notification.intent.SubmissionCompletedIntent;
import com.ulticode.modules.submission.entity.Submission;
import com.ulticode.modules.submission.enums.SubmissionStatus;
import com.ulticode.modules.submission.port.ProblemFactsPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Deep module owning the submission-judged notification dispatch.
 *
 * <p>The verdict-write port holds this single seam: it normalises runtime /
 * memory, resolves the problem title, and dispatches one typed
 * {@link SubmissionCompletedIntent}. The {@link NotificationDispatcher} then
 * owns the entire delivery policy — preference gating, per-channel fan-out
 * (InApp row, Email for terminal outcomes, WebSocket push), and ledger-backed
 * idempotency. Both write paths in {@code DefaultSubmissionWritePort} call
 * this module through the same method, so a bug fix hits every caller.
 *
 * <p>The dispatch is fire-and-forget — failures are logged and never
 * propagated to the verdict writer, matching the ADR-004 §2.5 failure-
 * isolation contract.
 *
 * @author ulticode
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JudgedNotificationDispatcher {

    private final NotificationDispatcher notificationDispatcher;
    private final ProblemFactsPort problemFacts;

    /**
     * Dispatch the post-verdict notification. Both the legacy (unfenced) and
     * fenced paths in {@code DefaultSubmissionWritePort} converge here.
     *
     * @param submission the submission entity (canonical row after the verdict
     *                   wrote; the unfenced path passes the entity it just
     *                   inserted)
     * @param status     the typed verdict
     * @param elapsedMs  runtime in ms; values < 0 are clamped to 0
     * @param memoryMb   memory in MB; {@code null} treated as 0 MB
     */
    public void dispatch(Submission submission, SubmissionStatus status, long elapsedMs, Double memoryMb) {
        try {
            long memBytes = memoryMb == null ? 0L : (long) (memoryMb * 1024 * 1024);
            long safeElapsed = Math.max(0L, elapsedMs);
            ProblemFactsPort.ProblemDisplayFacts facts = problemFacts.findDisplayFacts(submission.getProblemId());
            String problemTitle = facts != null ? facts.title() : "";
            notificationDispatcher.dispatch(
                    SubmissionCompletedIntent.of(
                            submission,
                            status,
                            problemTitle,
                            safeElapsed,
                            memBytes,
                            null,
                            null));
        } catch (Exception e) {
            log.warn("Failed to create submission notification for submission {}: {}",
                    submission.getId(), e.getMessage());
        }
    }
}
