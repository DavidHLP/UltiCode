package com.ulticode.modules.achievement.consumer;

import com.ulticode.app.api.service.ContestSubmissionPort;
import com.ulticode.submission.api.service.SubmissionUserStatsPort;
import com.ulticode.domain.submission.enums.SubmissionStatus;
import com.ulticode.modules.achievement.constants.AchievementType;
import com.ulticode.modules.achievement.service.AchievementTriggerService;
import com.ulticode.modules.submission.codec.SubmissionStatusCodec;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

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
        String submissionId = requiredString(payload, "submissionId");
        String userId = requiredString(payload, "userId");
        SubmissionStatus status = SubmissionStatusCodec.fromWire(
                requiredString(payload, "verdict"));
        long generation = requiredLong(payload, "generation");
        if (generation < 0) {
            throw new IllegalArgumentException("Negative SubmissionJudged generation");
        }

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

    private static String requiredString(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value == null || String.valueOf(value).isBlank()) {
            throw new IllegalArgumentException("Missing SubmissionJudged field: " + key);
        }
        return String.valueOf(value);
    }

    private static long requiredLong(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value == null) {
            throw new IllegalArgumentException("Missing SubmissionJudged field: " + key);
        }
        try {
            long parsed = value instanceof Number number
                    ? exactLong(number)
                    : Long.parseLong(String.valueOf(value));
            if (parsed < 0) {
                throw new IllegalArgumentException("Negative SubmissionJudged field: " + key);
            }
            return parsed;
        } catch (NumberFormatException | ArithmeticException e) {
            throw new IllegalArgumentException("Invalid SubmissionJudged field: " + key, e);
        }
    }

    private static long exactLong(Number number) {
        if (number instanceof Byte || number instanceof Short
                || number instanceof Integer || number instanceof Long) {
            return number.longValue();
        }
        return new java.math.BigDecimal(String.valueOf(number)).longValueExact();
    }

    private static int toInt(Long value) {
        if (value == null || value <= 0) {
            return 0;
        }
        return value >= Integer.MAX_VALUE ? Integer.MAX_VALUE : value.intValue();
    }
}
