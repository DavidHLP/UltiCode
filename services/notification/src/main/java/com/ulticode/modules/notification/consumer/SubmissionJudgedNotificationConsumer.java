package com.ulticode.modules.notification.consumer;

import com.ulticode.domain.submission.enums.SubmissionStatus;
import com.ulticode.modules.notification.dispatcher.NotificationDispatcher;
import com.ulticode.modules.notification.entity.enums.NotificationCategory;
import com.ulticode.modules.notification.intent.SubmissionCompletedIntent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

import static com.ulticode.submission.api.event.SubmissionLifecycleEventContract.GENERATION;
import static com.ulticode.submission.api.event.SubmissionLifecycleEventContract.PROBLEM_ID;
import static com.ulticode.submission.api.event.SubmissionLifecycleEventContract.SUBMISSION_ID;
import static com.ulticode.submission.api.event.SubmissionLifecycleEventContract.USER_ID;
import static com.ulticode.submission.api.event.SubmissionLifecycleEventContract.legacyJudgedMemoryMb;
import static com.ulticode.submission.api.event.SubmissionLifecycleEventContract.legacyJudgedRuntimeMs;
import static com.ulticode.submission.api.event.SubmissionLifecycleEventContract.optionalJudgedString;
import static com.ulticode.submission.api.event.SubmissionLifecycleEventContract.requiredJudgedLong;
import static com.ulticode.submission.api.event.SubmissionLifecycleEventContract.requiredJudgedStatus;
import static com.ulticode.submission.api.event.SubmissionLifecycleEventContract.requiredJudgedString;

/**
 * Handles the durable {@code SubmissionJudged} event for Notification.
 *
 * <p>The inbox owns event identity and retry state. This handler only translates
 * the stable event payload into the existing notification intent seam, preserving
 * the channel fan-out and ledger semantics behind {@link NotificationDispatcher}.
 */
@Component
@RequiredArgsConstructor
public class SubmissionJudgedNotificationConsumer {

    private final NotificationDispatcher notificationDispatcher;

    /**
     * Dispatch one persisted {@code SubmissionJudged} payload.
     *
     * <p>Malformed identity or verdict data is rejected so the inbox can retry
     * or move the row to its dead-letter state instead of acknowledging it.
     */
    public void consume(Map<String, Object> payload) {
        String submissionId = requiredJudgedString(payload, SUBMISSION_ID);
        String userId = requiredJudgedString(payload, USER_ID);
        SubmissionStatus status = requiredJudgedStatus(payload);

        if (!status.isTerminal()) {
            return;
        }

        long generation = requiredJudgedLong(payload, GENERATION);
        long elapsedMs = legacyJudgedRuntimeMs(payload);
        long memoryBytes = Math.max(0L, (long) (legacyJudgedMemoryMb(payload) * 1024L * 1024L));
        String problemId = optionalJudgedString(payload, PROBLEM_ID);

        SubmissionCompletedIntent intent = new SubmissionCompletedIntent(
                userId,
                submissionId,
                generation,
                status,
                problemId,
                null,
                elapsedMs,
                memoryBytes,
                null,
                null,
                NotificationCategory.SYSTEM);
        notificationDispatcher.dispatchForDurableRetry(intent);
    }
}
