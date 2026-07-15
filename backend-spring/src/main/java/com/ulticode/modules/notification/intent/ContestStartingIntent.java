package com.ulticode.modules.notification.intent;

import com.ulticode.modules.contest.entity.Contest;
import com.ulticode.modules.contest.entity.ContestParticipant;
import com.ulticode.modules.notification.entity.enums.NotificationCategory;
import com.ulticode.modules.websocket.notification.dto.NotificationPayload;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Intent for reminding a participant that a contest is starting soon.
 *
 * <p><b>Status: active.</b> Constructed by
 * {@code ContestLifecycleServiceImpl#sendContestReminder} for every T-24h /
 * T-1h contest-start reminder; the {@link com.ulticode.modules.notification.dispatcher.NotificationDispatcher}
 * then fans out to the InApp, Email, and WebSocket channels with
 * ledger-backed idempotency.
 *
 * <p>The {@code reminderType} is one of {@code "24h"} / {@code "1h"}; it is part
 * of the intent id so the 24h and 1h reminders for the same user/contest
 * are distinct intents.
 *
 * <p>Reference: ADR-004 §2.1.
 */
public record ContestStartingIntent(
        String userId,
        String contestId,
        String contestTitle,
        LocalDateTime startTime,
        String reminderType,
        NotificationCategory category
) implements NotificationIntent {

    @Override
    public String intentId() {
        return "contest:" + userId + ":" + contestId + ":" + reminderType;
    }

    @Override
    public NotificationPayload toPushPayload() {
        return NotificationPayload.of(
                intentId(),
                "CONTEST_REMINDER",
                "Contest '" + contestTitle + "' starts in " + reminderType,
                "",
                Map.of(
                        "contestId", contestId,
                        "reminderType", reminderType,
                        "startTime", startTime == null ? "" : startTime.toString()));
    }

    /**
     * Build from a {@link Contest}, a {@link ContestParticipant}, and a
     * reminder-type string ({@code "24h"} or {@code "1h"}).
     */
    public static ContestStartingIntent of(Contest contest,
                                           ContestParticipant participant,
                                           String reminderType) {
        return new ContestStartingIntent(
                participant.getUserId(),
                contest.getId(),
                contest.getTitle(),
                contest.getStartTime(),
                reminderType,
                NotificationCategory.SYSTEM
        );
    }
}
