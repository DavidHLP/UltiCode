package com.ulticode.modules.notification.intent;

import com.ulticode.modules.notification.entity.enums.NotificationCategory;
import com.ulticode.domain.submission.enums.SubmissionStatus;
import com.ulticode.notification.api.dto.NotificationPayload;

import java.util.Map;

/**
 * Intent emitted when a submission transitions to a terminal status.
 *
 * <p>Fields are kept maximal on purpose: even channels that do not use
 * {@code memoryBytes} today (e.g. email) keep the field so a future template
 * does not need a schema change.
 *
 * <p>{@link #intentId()} is derived from {@code submissionId + ":" + generation}
 * — the ADR-003 generation fence guarantees that a re-judge produces a new
 * generation and therefore a new intent id, so retries are correctly deduped
 * while distinct verdicts are not collapsed.
 *
 * <p>The {@code contestId} / {@code contestScoreDelta} pair is nullable: most
 * submissions are not part of a contest.
 *
 * <p>Reference: ADR-001 (SubmissionStatus), ADR-003 (generation fence).
 */
public record SubmissionCompletedIntent(
        String userId,
        String submissionId,
        long generation,
        SubmissionStatus status,
        String problemId,
        String problemTitle,
        long elapsedMs,
        long memoryBytes,
        String contestId,
        Long contestScoreDelta,
        NotificationCategory category
) implements NotificationIntent {

    @Override
    public String intentId() {
        return "submission:" + submissionId + ":g" + generation;
    }

    @Override
    public String wireType() {
        return "SUBMISSION";
    }

    @Override
    public NotificationPayload toPushPayload() {
        return NotificationPayload.of(
                intentId(),
                "SUBMISSION",
                "Submission judged: " + status.wireValue(),
                problemTitle == null ? "" : problemTitle,
                Map.of(
                        "submissionId", submissionId,
                        "problemId", problemId == null ? "" : problemId,
                        "status", status.wireValue(),
                        "isAccepted", status == SubmissionStatus.ACCEPTED,
                        "elapsedMs", elapsedMs,
                        "memoryBytes", memoryBytes));
    }

}
