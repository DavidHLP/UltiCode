package com.ulticode.modules.problem.port;

import com.ulticode.modules.problem.mapper.ProblemMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P3-OWNER-001-A: unit test for {@link DefaultProblemOwnerPort}.
 *
 * <p>The port is the owner-only write surface for the {@code problems}
 * row. These tests pin the contract that the legacy admin code
 * relies on, so a future regression (e.g., the port skipping the
 * underlying mapper) is caught at the seam.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("DefaultProblemOwnerPort")
class DefaultProblemOwnerPortTest {

    @Mock
    private ProblemMapper problemMapper;

    private ProblemOwnerPort port;

    @BeforeEach
    void setUp() {
        port = new DefaultProblemOwnerPort(problemMapper);
    }

    @Nested
    @DisplayName("flagProblem()")
    class FlagProblem {

        @Test
        @DisplayName("forwards id/reason/reportedBy to ProblemMapper.flagProblem")
        void forwardsToMapper() {
            when(problemMapper.flagProblem(eq(7L), eq("spam"), eq("admin-1"))).thenReturn(1);
            port.flagProblem(7L, "spam", "admin-1");
            verify(problemMapper).flagProblem(7L, "spam", "admin-1");
        }

        @Test
        @DisplayName("does not read ProblemMapper (write-only path)")
        void noRead() {
            when(problemMapper.flagProblem(anyLong(), anyString(), anyString())).thenReturn(1);
            port.flagProblem(1L, "x", "y");
            verify(problemMapper, never()).selectById(any());
            verify(problemMapper, never()).selectList(any());
        }
    }

    @Nested
    @DisplayName("moderateProblem()")
    class ModerateProblem {

        @Test
        @DisplayName("forwards id/status/notes/reviewedBy to ProblemMapper.moderateProblem")
        void forwardsToMapper() {
            when(problemMapper.moderateProblem(eq(7L), eq("DISMISSED"), eq("ok"), eq("admin-1")))
                    .thenReturn(1);
            port.moderateProblem(7L, "DISMISSED", "ok", "admin-1");
            verify(problemMapper).moderateProblem(7L, "DISMISSED", "ok", "admin-1");
        }
    }

    @Nested
    @DisplayName("restoreDeletedByIds()")
    class RestoreDeletedByIds {

        @Test
        @DisplayName("returns 0 for null/empty without touching the mapper")
        void emptyInputIsNoop() {
            assertThat(port.restoreDeletedByIds(null)).isZero();
            assertThat(port.restoreDeletedByIds(List.of())).isZero();
            verify(problemMapper, never()).restoreDeletedByIds(any());
        }

        @Test
        @DisplayName("forwards non-empty list to mapper and returns the affected count")
        void forwardsNonEmpty() {
            when(problemMapper.restoreDeletedByIds(List.of(1L, 2L, 3L))).thenReturn(3);
            int restored = port.restoreDeletedByIds(List.of(1L, 2L, 3L));
            assertThat(restored).isEqualTo(3);
            verify(problemMapper).restoreDeletedByIds(List.of(1L, 2L, 3L));
        }
    }

    @Nested
    @DisplayName("moderateProblems() (bulk)")
    class ModerateProblemsBulk {

        @Test
        @DisplayName("returns 0 for null/empty without touching the mapper")
        void emptyInputIsNoop() {
            assertThat(port.moderateProblems(null, "DISMISSED", "ok", "admin-1")).isZero();
            assertThat(port.moderateProblems(List.of(), "DISMISSED", "ok", "admin-1")).isZero();
            verify(problemMapper, never()).batchModerateProblems(any(), anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("forwards non-empty list to ProblemMapper.batchModerateProblems")
        void forwardsNonEmpty() {
            when(problemMapper.batchModerateProblems(List.of(1L, 2L), "DISMISSED", "ok", "admin-1"))
                    .thenReturn(2);
            int affected = port.moderateProblems(List.of(1L, 2L), "DISMISSED", "ok", "admin-1");
            assertThat(affected).isEqualTo(2);
            verify(problemMapper).batchModerateProblems(List.of(1L, 2L), "DISMISSED", "ok", "admin-1");
        }
    }

    @Nested
    @DisplayName("updateDifficulty()")
    class UpdateDifficulty {

        @Test
        @DisplayName("forwards id/difficulty to ProblemMapper.updateDifficulty")
        void forwardsToMapper() {
            when(problemMapper.updateDifficulty(eq(7L), eq("HARD"))).thenReturn(1);
            port.updateDifficulty(7L, "HARD");
            verify(problemMapper).updateDifficulty(7L, "HARD");
        }

        @Test
        @DisplayName("null id or blank difficulty is a no-op")
        void noopOnInvalidInput() {
            lenient().when(problemMapper.updateDifficulty(any(), any())).thenReturn(1);
            port.updateDifficulty(null, "HARD");
            port.updateDifficulty(7L, null);
            port.updateDifficulty(7L, "  ");
            port.updateDifficulty(7L, "");
            verify(problemMapper, never()).updateDifficulty(anyLong(), anyString());
        }
    }
}
