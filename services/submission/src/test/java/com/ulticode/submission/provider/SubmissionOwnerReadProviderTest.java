package com.ulticode.submission.provider;

import com.ulticode.app.api.service.ProblemFactsPort;
import com.ulticode.common.dto.DifficultyCountDTO;
import com.ulticode.modules.submission.mapper.SubmissionMapper;
import com.ulticode.submission.api.dto.DailyActiveUserCount;
import com.ulticode.submission.api.dto.ProblemDifficultyCompletion;
import com.ulticode.submission.api.dto.SubmissionDateCountDTO;
import com.ulticode.submission.api.dto.SubmissionAdjudicationFact;
import com.ulticode.submission.api.dto.WeeklyActiveUserCount;
import com.ulticode.submission.dubbo.provider.SubmissionAdjudicationReadProvider;
import com.ulticode.submission.dubbo.provider.ProblemSubmissionStatsProvider;
import com.ulticode.submission.dubbo.provider.SubmissionActivityAnalyticsProvider;
import com.ulticode.submission.dubbo.provider.SubmissionGenerationReadProvider;
import com.ulticode.submission.dubbo.provider.SubmissionStreakProvider;
import com.ulticode.submission.dubbo.provider.SubmissionUserStatsProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubmissionOwnerReadProviderTest {

    @Mock
    private SubmissionMapper submissionMapper;
    @Mock
    private ProblemFactsPort problemFacts;

    @Test
    void userStatsProviderReadsSubmissionOwnerAndEnrichesDifficultyThroughFacts() {
        when(submissionMapper.countAcceptedProblemsByUserId("user-1")).thenReturn(3L);
        when(submissionMapper.countByUserId("user-1")).thenReturn(5L);
        when(submissionMapper.calculateAcceptanceRateByUserId("user-1")).thenReturn(60.0);
        when(submissionMapper.findGlobalRankByUserId("user-1")).thenReturn(2);
        when(submissionMapper.findAcceptedProblemIdsByUserId("user-1"))
                .thenReturn(List.of(1L, 2L, 3L));
        when(problemFacts.findContestProblemFacts(1L)).thenReturn(
                new ProblemFactsPort.ContestProblemFacts(1L, "One", "one", "EASY", null));
        when(problemFacts.findContestProblemFacts(2L)).thenReturn(
                new ProblemFactsPort.ContestProblemFacts(2L, "Two", "two", "Medium", null));
        when(problemFacts.findContestProblemFacts(3L)).thenReturn(
                new ProblemFactsPort.ContestProblemFacts(3L, "Three", "three", "easy", null));
        when(submissionMapper.findSubmissionCountsByDate("user-1", 2026))
                .thenReturn(List.of(new SubmissionDateCountDTO("2026-08-30", 2L)));

        SubmissionUserStatsProvider provider = new SubmissionUserStatsProvider(
                submissionMapper, problemFacts);

        assertThat(provider.countAcceptedProblemsByUserId("user-1")).isEqualTo(3L);
        assertThat(provider.countByUserId("user-1")).isEqualTo(5L);
        assertThat(provider.calculateAcceptanceRateByUserId("user-1")).isEqualTo(60.0);
        assertThat(provider.findGlobalRankByUserId("user-1")).isEqualTo(2);
        assertThat(provider.countAcceptedProblemsByDifficulty("user-1"))
                .extracting(DifficultyCountDTO::getDifficulty, DifficultyCountDTO::getCount)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("Easy", 2L),
                        org.assertj.core.groups.Tuple.tuple("Medium", 1L));
        assertThat(provider.findSubmissionCountsByDate("user-1", 2026))
                .extracting(SubmissionDateCountDTO::getDate)
                .containsExactly("2026-08-30");
    }

    @Test
    void activityProviderReturnsTypedOwnerAggregates() {
        LocalDateTime start = LocalDateTime.of(2026, 8, 1, 0, 0);
        LocalDateTime end = start.plusDays(1);
        when(submissionMapper.countDailyActiveUsers(start, end))
                .thenReturn(List.of(new DailyActiveUserCount("2026-08-01", 4L)));
        when(submissionMapper.countWeeklyActiveUsers(start))
                .thenReturn(List.of(new WeeklyActiveUserCount("2026-07-27", 202630, 4L)));

        SubmissionActivityAnalyticsProvider provider =
                new SubmissionActivityAnalyticsProvider(submissionMapper);

        assertThat(provider.countDailyActiveUsers(start, end)).hasSize(1);
        assertThat(provider.countWeeklyActiveUsers(start)).hasSize(1);
        verify(submissionMapper).countWeeklyActiveUsers(start);
    }

    @Test
    void problemStatsProviderUsesCallerSuppliedProblemFacts() {
        when(submissionMapper.countAcceptedByProblem()).thenReturn(List.of(
                new SubmissionMapper.ProblemAcceptanceCount(101L, 4L),
                new SubmissionMapper.ProblemAcceptanceCount(102L, 1L)));
        ProblemSubmissionStatsProvider provider = new ProblemSubmissionStatsProvider(submissionMapper);

        List<ProblemDifficultyCompletion> result = provider.countProblemCompletionByDifficulty(
                Map.of(101L, "Easy", 102L, "HARD"));

        assertThat(result).extracting(ProblemDifficultyCompletion::getDifficulty)
                .containsExactlyInAnyOrder("EASY", "HARD");
        assertThat(result).extracting(ProblemDifficultyCompletion::getSolvedProblems)
                .containsExactly(1L, 1L);
        assertThat(result).extracting(ProblemDifficultyCompletion::getTotalProblems)
                .containsExactly(1L, 1L);
    }

    @Test
    void boundedOwnerReadsExposeBatchCountsAndAdjudicationFacts() {
        when(submissionMapper.countByProblemIds(List.of(101L, 102L))).thenReturn(List.of(
                new SubmissionMapper.ProblemSubmissionCount(101L, 4L)));
        when(submissionMapper.findAdjudicationFactsByIds(List.of("submission-1")))
                .thenReturn(List.of(new SubmissionAdjudicationFact("submission-1", 3L, "Accepted")));

        ProblemSubmissionStatsProvider stats = new ProblemSubmissionStatsProvider(submissionMapper);
        SubmissionAdjudicationReadProvider adjudication = new SubmissionAdjudicationReadProvider(
                submissionMapper);

        assertThat(stats.countByProblemIds(List.of(101L, 102L)))
                .containsEntry(101L, 4L)
                .doesNotContainKey(102L);
        assertThat(adjudication.findByIds(List.of("submission-1")))
                .containsExactly(new SubmissionAdjudicationFact("submission-1", 3L, "Accepted"));
    }

    @Test
    void streakAndGenerationProvidersNormalizeOwnerValues() {
        when(submissionMapper.calculateStreak("user-1")).thenReturn(null);
        when(submissionMapper.findGenerationForUpdate("submission-1")).thenReturn(7L);

        assertThat(new SubmissionStreakProvider(submissionMapper).computeStreak("user-1"))
                .isZero();
        assertThat(new SubmissionGenerationReadProvider(submissionMapper)
                .findGenerationForUpdate("submission-1")).isEqualTo(7L);
        verify(submissionMapper).calculateStreak("user-1");
    }
}
