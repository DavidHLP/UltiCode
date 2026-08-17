package com.ulticode.modules.achievement.consumer;

import com.ulticode.app.api.service.ContestSubmissionPort;
import com.ulticode.submission.api.service.SubmissionUserStatsPort;
import com.ulticode.modules.achievement.constants.AchievementType;
import com.ulticode.modules.achievement.service.AchievementTriggerService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubmissionJudgedAchievementConsumerTest {

    @Mock
    private AchievementTriggerService achievementTriggerService;
    @Mock
    private SubmissionUserStatsPort submissionUserStats;
    @Mock
    private ContestSubmissionPort contestSubmissionPort;

    @Test
    void acceptedNonVirtualEventChecksBothSubmissionAchievementFamilies() {
        when(contestSubmissionPort.isVirtualParticipation("submission-1")).thenReturn(false);
        when(submissionUserStats.countAcceptedProblemsByUserId("user-1")).thenReturn(4L);
        when(submissionUserStats.countByUserId("user-1")).thenReturn(9L);
        SubmissionJudgedAchievementConsumer consumer = consumer();

        consumer.consume(payload("Accepted"));

        verify(achievementTriggerService).checkAndAwardAchievements(
                "user-1", AchievementType.PROBLEMS_SOLVED, 4);
        verify(achievementTriggerService).checkAndAwardAchievements(
                "user-1", AchievementType.SUBMISSIONS_MADE, 9);
    }

    @Test
    void virtualReplayDoesNotReadCountsOrAwardAchievements() {
        when(contestSubmissionPort.isVirtualParticipation("submission-1")).thenReturn(true);
        SubmissionJudgedAchievementConsumer consumer = consumer();

        consumer.consume(payload("Accepted"));

        verifyNoInteractions(submissionUserStats, achievementTriggerService);
    }

    @Test
    void nonAcceptedEventDoesNotQueryContestOrAchievementState() {
        SubmissionJudgedAchievementConsumer consumer = consumer();

        consumer.consume(payload("Wrong Answer"));

        verifyNoInteractions(contestSubmissionPort, submissionUserStats, achievementTriggerService);
    }

    @Test
    void awardFailurePropagatesSoInboxCanRetry() {
        when(contestSubmissionPort.isVirtualParticipation("submission-1")).thenReturn(false);
        when(submissionUserStats.countAcceptedProblemsByUserId("user-1")).thenReturn(1L);
        when(achievementTriggerService.checkAndAwardAchievements(
                eq("user-1"), eq(AchievementType.PROBLEMS_SOLVED), eq(1)))
                .thenThrow(new IllegalStateException("achievement store unavailable"));
        SubmissionJudgedAchievementConsumer consumer = consumer();

        assertThatThrownBy(() -> consumer.consume(payload("Accepted")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("achievement store unavailable");
    }

    private SubmissionJudgedAchievementConsumer consumer() {
        return new SubmissionJudgedAchievementConsumer(
                achievementTriggerService, submissionUserStats, contestSubmissionPort);
    }

    private static Map<String, Object> payload(String verdict) {
        return Map.of(
                "submissionId", "submission-1",
                "userId", "user-1",
                "generation", 3,
                "verdict", verdict);
    }
}
