package com.ulticode.modules.notification.intent;

import com.ulticode.modules.contest.entity.Contest;
import com.ulticode.modules.contest.entity.ContestParticipant;
import com.ulticode.modules.notification.entity.enums.NotificationCategory;

import java.time.LocalDateTime;

/**
 * Intent emitted by {@code ContestScheduler} to remind a participant that a
 * contest is starting soon. Replaces the legacy per-reminder
 * {@code Map<String, Object>} dispatch in
 * {@code ContestScheduler.sendContestReminder}.
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
