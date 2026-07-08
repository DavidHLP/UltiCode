package com.ulticode.modules.submission.stats;

import com.ulticode.modules.submission.mapper.SubmissionMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link JdbcSubmissionStreakCalculator}.
 *
 * <p>The whole point of the deep module: the only behaviour worth pinning
 * with a test is (1) it delegates to {@code SubmissionMapper#calculateStreak}
 * with the same {@code userId} argument, and (2) the mapper's nullable
 * {@code Integer} return is collapsed to primitive {@code 0} when null.
 * The recursive-CTE itself stays in SQL — that is exercised by the
 * integration tests against a real MySQL container, not here.
 *
 * <p>The fact that this test mocks only the mapper (one collaborator) is
 * the proof that the interface is the test surface: every other caller in
 * the codebase can swap in a mock of {@link SubmissionStreakCalculator}
 * instead of mocking the mapper directly.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("JdbcSubmissionStreakCalculator")
class SubmissionStreakCalculatorTest {

    @Mock
    private SubmissionMapper submissionMapper;

    @InjectMocks
    private JdbcSubmissionStreakCalculator calculator;

    @Nested
    @DisplayName("delegation to SubmissionMapper")
    class Delegation {

        @Test
        @DisplayName("computeStreak forwards the userId unchanged")
        void forwardsUserId() {
            String userId = "u-7";
            when(submissionMapper.calculateStreak(userId)).thenReturn(4);

            int result = calculator.computeStreak(userId);

            assertThat(result).isEqualTo(4);
            verify(submissionMapper).calculateStreak(userId);
            verifyNoMoreInteractions(submissionMapper);
        }

        @Test
        @DisplayName("computeStreak returns the mapper's non-null value as-is")
        void valuePassesThrough() {
            when(submissionMapper.calculateStreak("u-2")).thenReturn(365);

            assertThat(calculator.computeStreak("u-2")).isEqualTo(365);
        }
    }

    @Nested
    @DisplayName("null coercion (mapper null -> primitive 0)")
    class NullCoercion {

        @Test
        @DisplayName("mapper returning null yields 0 (no NPE, no leaked nullability)")
        void nullFromMapper_yieldsZero() {
            when(submissionMapper.calculateStreak("u-ghost")).thenReturn(null);

            assertThat(calculator.computeStreak("u-ghost")).isZero();
        }
    }
}
