package com.ulticode.modules.submission.dispatcher;

import com.ulticode.app.api.service.SubmissionNotificationPort;
import com.ulticode.modules.submission.entity.Submission;
import com.ulticode.domain.submission.enums.SubmissionStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Deep module owning the submission-judged notification dispatch.
 *
 * <p>The verdict-write port holds this single seam: it normalises runtime /
 * memory, resolves the problem title, and dispatches through
 * {@link SubmissionNotificationPort}. The notification port implementation
 * (supplied by the notification module) owns the delivery policy —
 * preference gating, per-channel fan-out, and ledger-backed idempotency.
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

    private final SubmissionNotificationPort notificationPort;

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
    public void dispatch(Submission submission, SubmissionStatus status,
                         long elapsedMs, Double memoryMb) {
        try {
            long safeElapsed = Math.max(0L, elapsedMs);
            notificationPort.dispatchSubmissionCompleted(
                    submission.getId(),
                    submission.getUserId(),
                    submission.getProblemId(),
                    status == SubmissionStatus.ACCEPTED,
                    status.name());
        } catch (Exception e) {
            log.warn("Failed to dispatch submission notification for submission {}: {}",
                    submission.getId(), e.getMessage());
        }
    }
}
