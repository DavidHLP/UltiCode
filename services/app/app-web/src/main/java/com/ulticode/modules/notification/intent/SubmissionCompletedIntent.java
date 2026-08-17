package com.ulticode.modules.notification.intent;

import com.ulticode.modules.notification.entity.enums.NotificationCategory;
import com.ulticode.modules.submission.entity.Submission;
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

    /**
     * Build a {@link SubmissionCompletedIntent} from a persisted {@link Submission}
     * + a verified {@link SubmissionStatus} (parsed from the wire / db value).
     *
     * <p>The {@code problemTitle} is supplied separately so the caller can
     * pre-load it without the channel having to re-query. {@code contestId} and
     * {@code contestScoreDelta} default to {@code null} for non-contest
     * submissions; pass non-null values from the contest module to surface
     * ranking-impact context.
     *
     * <p><b>generation is required.</b> The factory throws
     * {@link IllegalStateException} when {@code submission.getGeneration()}
     * is null rather than silently falling back to {@code 0L} — a null
     * generation indicates a transient hydration bug (the field is
     * defaulted to {@code 1L} on the entity, so null is never valid
     * business data). Silently substituting 0 would collide with a real
     * generation-0 submission's intentId and drop the second dispatch
     * (ADR-004 M4d-1 review finding #5).
     */
    public static SubmissionCompletedIntent of(Submission submission,
                                                SubmissionStatus status,
                                                String problemTitle,
                                                long elapsedMs,
                                                long memoryBytes,
                                                String contestId,
                                                Long contestScoreDelta) {
        Long gen = submission.getGeneration();
        if (gen == null) {
            throw new IllegalStateException(
                    "Submission " + submission.getId() + " has null generation; "
                            + "the row is not yet hydrated or the schema is broken");
        }
        return new SubmissionCompletedIntent(
                submission.getUserId(),
                submission.getId(),
                gen,
                status,
                submission.getProblemId() == null ? null : String.valueOf(submission.getProblemId()),
                problemTitle,
                elapsedMs,
                memoryBytes,
                contestId,
                contestScoreDelta,
                NotificationCategory.SYSTEM
        );
    }
}
