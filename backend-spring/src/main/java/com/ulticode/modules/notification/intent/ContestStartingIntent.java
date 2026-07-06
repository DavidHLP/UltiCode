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
 * <p><b>Status: reserved, not yet wired (ADR-004 M4c pending).</b> The type,
 * channels, and dispatcher projection are implemented, but
 * {@code ContestScheduler} still dispatches contest reminders through the
 * legacy {@code NotificationDispatchService} path. No production caller
 * constructs this intent yet; it activates once the contest module migrates
 * to the typed dispatcher behind {@code FeatureFlags.useNotificationIntent}.
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
