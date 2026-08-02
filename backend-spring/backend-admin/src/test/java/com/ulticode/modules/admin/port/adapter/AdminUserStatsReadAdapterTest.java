package com.ulticode.modules.admin.port.adapter;

import com.ulticode.modules.solution.mapper.SolutionMapper;
import com.ulticode.app.api.service.SubmissionUserStatsPort;
import com.ulticode.modules.submission.stats.SubmissionStreakCalculator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AdminUserStatsReadAdapter}.
 *
 * <p>The adapter's single responsibility is null→primitive coercion. These
 * tests pin that contract so the port interface can stay null-free and
 * callers (e.g. {@code AdminUserServiceImpl}) never re-implement null handling.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AdminUserStatsReadAdapter")
class AdminUserStatsReadAdapterTest {

    @Mock
    private SubmissionUserStatsPort submissionUserStats;

    @Mock
    private SubmissionStreakCalculator submissionStreakCalculator;

    @Mock
    private SolutionMapper solutionMapper;

    @InjectMocks
    private AdminUserStatsReadAdapter adapter;

    @Nested
    @DisplayName("null coercion (mapper null → primitive 0)")
    class NullCoercion {

        @Test
        @DisplayName("all mapper nulls yield zero stats")
        void allNull_yieldsZero() {
            String userId = "user-123";
            when(submissionUserStats.countByUserId(userId)).thenReturn(null);
            when(submissionUserStats.countAcceptedProblemsByUserId(userId)).thenReturn(null);
            when(submissionStreakCalculator.computeStreak(userId)).thenReturn(0);
            when(solutionMapper.countByUserId(userId)).thenReturn(null);

            assertThat(adapter.countSubmissionsByUserId(userId)).isEqualTo(0L);
            assertThat(adapter.countAcceptedProblemsByUserId(userId)).isEqualTo(0L);
            assertThat(adapter.countSolutionsByUserId(userId)).isEqualTo(0L);
            assertThat(adapter.calculateSubmissionStreak(userId)).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("value pass-through")
    class PassThrough {

        @Test
        @DisplayName("mapper values are returned as primitives")
        void values_passedThrough() {
            String userId = "user-456";
            when(submissionUserStats.countByUserId(userId)).thenReturn(42L);
            when(submissionUserStats.countAcceptedProblemsByUserId(userId)).thenReturn(17L);
            when(submissionStreakCalculator.computeStreak(userId)).thenReturn(9);
            when(solutionMapper.countByUserId(userId)).thenReturn(5L);

            assertThat(adapter.countSubmissionsByUserId(userId)).isEqualTo(42L);
            assertThat(adapter.countAcceptedProblemsByUserId(userId)).isEqualTo(17L);
            assertThat(adapter.countSolutionsByUserId(userId)).isEqualTo(5L);
            assertThat(adapter.calculateSubmissionStreak(userId)).isEqualTo(9);
        }
    }
}
