package com.ulticode.modules.submission.dispatcher;

import com.ulticode.common.config.FeatureFlagsProperties;
import com.ulticode.modules.notification.dispatcher.NotificationDispatcher;
import com.ulticode.modules.notification.service.NotificationDispatchService;
import com.ulticode.modules.problem.entity.Problem;
import com.ulticode.modules.problem.mapper.ProblemMapper;
import com.ulticode.modules.submission.entity.Submission;
import com.ulticode.modules.submission.enums.SubmissionStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Deep module owning the submission-judged notification dispatch.
 *
 * <p>Replaces the twin {@code dispatchJudgedNotificationLegacyPath} /
 * {@code dispatchJudgedNotificationFencedPath} methods that used to live in
 * {@code DefaultSubmissionWritePort}. Both paths are 95% identical — they
 * normalise runtime / memory, parse the status enum, then dispatch either
 * the typed {@code SubmissionCompletedIntent} (when
 * {@link FeatureFlagsProperties#isUseNotificationIntent()} is on) or fall
 * back to the legacy {@code NotificationDispatchService} envelope.
 *
 * <p>After the deepening, the verdict-write port holds one seam — this
 * dispatcher — and one collaborator (it owns the flag check, the
 * problem-title lookup, the legacy-vs-intent switch, and the fire-and-forget
 * exception isolation). Both write paths in
 * {@code DefaultSubmissionWritePort} call this module through the same
 * method, so a bug fix hits every caller and the port's constructor drops
 * the two extra notification collaborators.
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

    private final FeatureFlagsProperties featureFlags;
    private final NotificationDispatcher notificationDispatcher;
    private final NotificationDispatchService notificationDispatchService;
    private final ProblemMapper problemMapper;

    /**
     * Dispatch the post-verdict notification. Both the legacy (unfenced) and
     * fenced paths in {@code DefaultSubmissionWritePort} converge here, so the
     * 95% duplication disappears and a future flag-day cleanup touches one
     * method.
     *
     * @param submission the submission entity (canonical row after the verdict
     *                   wrote; the unfenced path passes the entity it just
     *                   inserted)
     * @param status     wire-string status (e.g. {@code "Accepted"})
     * @param elapsedMs  runtime in ms; values < 0 are clamped to 0
     * @param memoryMb   memory in MB; {@code null} treated as 0 MB
     */
    public void dispatch(Submission submission, String status, long elapsedMs, Double memoryMb) {
        try {
            long memBytes = memoryMb == null ? 0L : (long) (memoryMb * 1024 * 1024);
            long safeElapsed = Math.max(0L, elapsedMs);
            if (featureFlags.isUseNotificationIntent()) {
                Problem problem = problemMapper.selectById(submission.getProblemId());
                SubmissionStatus statusEnum = SubmissionStatus.fromDbName(status);
                notificationDispatcher.dispatch(
                        com.ulticode.modules.notification.intent.SubmissionCompletedIntent.of(
                                submission,
                                statusEnum != null ? statusEnum : SubmissionStatus.SYSTEM_ERROR,
                                problem != null ? problem.getTitle() : "",
                                safeElapsed,
                                memBytes,
                                null,
                                null));
            } else {
                Problem problem = problemMapper.selectById(submission.getProblemId());
                notificationDispatchService.dispatch(
                        submission.getUserId(),
                        "SUBMISSION",
                        "SYSTEM",
                        "Submission judged: " + status,
                        "",
                        "/submissions/" + submission.getId(),
                        Map.of(
                                "submissionId", submission.getId(),
                                "problemId", submission.getProblemId(),
                                "problemTitle", problem != null ? problem.getTitle() : "",
                                "status", status,
                                "isAccepted", "Accepted".equals(status)),
                        false);
            }
        } catch (Exception e) {
            log.warn("Failed to create submission notification for submission {}: {}",
                    submission.getId(), e.getMessage());
        }
    }
}