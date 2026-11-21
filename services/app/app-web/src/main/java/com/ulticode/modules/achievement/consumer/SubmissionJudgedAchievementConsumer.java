package com.ulticode.modules.achievement.consumer;

import com.ulticode.app.api.service.ContestSubmissionPort;
import com.ulticode.submission.api.service.SubmissionUserStatsPort;
import com.ulticode.domain.submission.enums.SubmissionStatus;
import com.ulticode.modules.achievement.constants.AchievementType;
import com.ulticode.modules.achievement.service.AchievementTriggerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

import static com.ulticode.submission.api.event.SubmissionLifecycleEventContract.GENERATION;
import static com.ulticode.submission.api.event.SubmissionLifecycleEventContract.SUBMISSION_ID;
import static com.ulticode.submission.api.event.SubmissionLifecycleEventContract.USER_ID;
import static com.ulticode.submission.api.event.SubmissionLifecycleEventContract.requiredJudgedLong;
import static com.ulticode.submission.api.event.SubmissionLifecycleEventContract.requiredJudgedStatus;
import static com.ulticode.submission.api.event.SubmissionLifecycleEventContract.requiredJudgedString;

/**
 * Handles accepted {@code SubmissionJudged} events for Achievement.
 *
 * <p>The durable event is the retry seam. Counts are read after the verdict
 * commit, and the achievement service remains the owner of award transactions
 * and earned-event publication.
 */
@Component
@RequiredArgsConstructor
public class SubmissionJudgedAchievementConsumer {

    private final AchievementTriggerService achievementTriggerService;
    private final SubmissionUserStatsPort submissionUserStats;
    private final ContestSubmissionPort contestSubmissionPort;

    /**
     * Evaluate accepted-submission achievement families once for this inbox row.
     */
    public void consume(Map<String, Object> payload) {
        String submissionId = requiredJudgedString(payload, SUBMISSION_ID);
        String userId = requiredJudgedString(payload, USER_ID);
        SubmissionStatus status = requiredJudgedStatus(payload);
        requiredJudgedLong(payload, GENERATION);

        if (status != SubmissionStatus.ACCEPTED
                || contestSubmissionPort.isVirtualParticipation(submissionId)) {
            return;
        }

        achievementTriggerService.checkAndAwardAchievements(
                userId,
                AchievementType.PROBLEMS_SOLVED,
                toInt(submissionUserStats.countAcceptedProblemsByUserId(userId)));
        achievementTriggerService.checkAndAwardAchievements(
                userId,
                AchievementType.SUBMISSIONS_MADE,
                toInt(submissionUserStats.countByUserId(userId)));
    }

    private static int toInt(Long value) {
        if (value == null || value <= 0) {
            return 0;
        }
        return value >= Integer.MAX_VALUE ? Integer.MAX_VALUE : value.intValue();
    }
}
