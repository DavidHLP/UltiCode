package com.ulticode.app.api.service;

import java.time.LocalDateTime;

/**
 * Contest notification port consumed by backend-app contest lifecycle service
 * to dispatch contest-start reminders after the family relocated from
 * backend-legacy.
 *
 * <p>Replaces direct {@code NotificationDispatcher} +
 * {@code ContestStartingIntent} imports that lived in the legacy
 * notification module.
 *
 * <p>P7-RELOCATE-CONTEST-001.
 *
 * @author ulticode
 */
public interface ContestNotificationPort {

    /**
     * Dispatch a contest-starting reminder to a participant.
     *
     * @param userId       the participant user id
     * @param contestId    the contest id
     * @param contestTitle the contest title
     * @param startTime    the contest start time
     * @param reminderType "24h" or "1h"
     */
    void notifyContestStarting(String userId, String contestId, String contestTitle,
                               LocalDateTime startTime, String reminderType);
}
