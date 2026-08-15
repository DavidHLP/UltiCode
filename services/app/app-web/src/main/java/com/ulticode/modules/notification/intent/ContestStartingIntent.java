package com.ulticode.modules.notification.intent;

import com.ulticode.modules.notification.entity.enums.NotificationCategory;
import com.ulticode.app.api.dto.NotificationPayload;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Intent for reminding a participant that a contest is starting soon.
 *
 * <p><b>Status: active.</b> Constructed by
 * {@code ContestLifecycleServiceImpl#sendContestReminder} for every T-24h /
 * T-1h contest-start reminder; the notification-owner dispatcher
 * ({@code com.ulticode.modules.notification.dispatcher.NotificationDispatcher},
 * moved with the NOTIFY-006 extraction) then fans out to the InApp, Email,
 * and WebSocket channels with ledger-backed idempotency.
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
    public String wireType() {
        return "CONTEST_REMINDER";
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
     * Build from native fields (P7-RELOCATE-CONTEST-001: replaces the
     * entity-based factory so this intent no longer imports contest entities).
     *
     * @param userId       the participant user id
     * @param contestId    the contest id
     * @param contestTitle the contest title
     * @param startTime    the contest start time
     * @param reminderType "24h" or "1h"
     */
    public static ContestStartingIntent of(String userId, String contestId, String contestTitle,
                                           LocalDateTime startTime, String reminderType) {
        return new ContestStartingIntent(
                userId,
                contestId,
                contestTitle,
                startTime,
                reminderType,
                NotificationCategory.SYSTEM
        );
    }
}
