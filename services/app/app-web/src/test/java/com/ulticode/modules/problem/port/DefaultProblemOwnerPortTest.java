package com.ulticode.modules.problem.port;
import com.ulticode.app.api.service.ProblemOwnerPort;

import com.ulticode.modules.problem.entity.Problem;
import com.ulticode.modules.problem.mapper.ProblemMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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

    @Mock
    private com.ulticode.modules.search.source.SearchDocumentChangedPublisher searchPublisher;

    private ProblemOwnerPort port;

    @BeforeEach
    void setUp() {
        port = new DefaultProblemOwnerPort(problemMapper, searchPublisher);
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

    @Nested
    @DisplayName("insertImportedProblem() (P3-BURNDOWN-001)")
    class InsertImportedProblem {

        @Test
        @DisplayName("applies import defaults when nullable fields are absent")
        void appliesDefaults() {
            port.insertImportedProblem("two-sum", "Two Sum", "Easy", null, null, null);

            ArgumentCaptor<Problem> captor = ArgumentCaptor.forClass(Problem.class);
            verify(problemMapper).insert(captor.capture());
            Problem inserted = captor.getValue();
            assertThat(inserted.getSlug()).isEqualTo("two-sum");
            assertThat(inserted.getTitle()).isEqualTo("Two Sum");
            assertThat(inserted.getDifficulty()).isEqualTo("Easy");
            assertThat(inserted.getStatus()).isEqualTo("todo");
            assertThat(inserted.getIsPremium()).isFalse();
            assertThat(inserted.getIsPublished()).isFalse();
            assertThat(inserted.getHasSolution()).isFalse();
            assertThat(inserted.getIsFlagged()).isFalse();
            assertThat(inserted.getIsDeleted()).isFalse();
            assertThat(inserted.getVersion()).isEqualTo(1);
        }

        @Test
        @DisplayName("honors explicit status / premium / published flags")
        void honorsExplicitFlags() {
            port.insertImportedProblem("three-sum", "Three Sum", "Hard", "solved", true, true);

            ArgumentCaptor<Problem> captor = ArgumentCaptor.forClass(Problem.class);
            verify(problemMapper).insert(captor.capture());
            Problem inserted = captor.getValue();
            assertThat(inserted.getStatus()).isEqualTo("solved");
            assertThat(inserted.getIsPremium()).isTrue();
            assertThat(inserted.getIsPublished()).isTrue();
        }
    }

    @Nested
    @DisplayName("applyImportedUpdate() (P3-BURNDOWN-001)")
    class ApplyImportedUpdate {

        @Test
        @DisplayName("merges non-null fields and leaves null DTO fields untouched")
        void mergesNonNullOnly() {
            Problem existing = new Problem();
            existing.setId(42L);
            existing.setSlug("dup");
            existing.setTitle("Old");
            existing.setDifficulty("Easy");
            existing.setStatus("todo");
            existing.setIsPremium(false);
            existing.setIsPublished(false);
            when(problemMapper.selectById(42L)).thenReturn(existing);

            port.applyImportedUpdate(42L, "New Title", "Hard", "solved", true, null);

            verify(problemMapper).updateById(existing);
            assertThat(existing.getTitle()).isEqualTo("New Title");
            assertThat(existing.getDifficulty()).isEqualTo("Hard");
            assertThat(existing.getStatus()).isEqualTo("solved");
            assertThat(existing.getIsPremium()).isTrue();
            // null DTO field must not overwrite the stored value
            assertThat(existing.getIsPublished()).isFalse();
        }

        @Test
        @DisplayName("blank strings are treated as 'no change' (PartialUpdate text semantics)")
        void blankStringsNoChange() {
            Problem existing = new Problem();
            existing.setId(42L);
            existing.setTitle("Old");
            existing.setDifficulty("Easy");
            existing.setStatus("todo");
            when(problemMapper.selectById(42L)).thenReturn(existing);

            port.applyImportedUpdate(42L, "", "   ", "", null, null);

            verify(problemMapper).updateById(existing);
            assertThat(existing.getTitle()).isEqualTo("Old");
            assertThat(existing.getDifficulty()).isEqualTo("Easy");
            assertThat(existing.getStatus()).isEqualTo("todo");
        }

        @Test
        @DisplayName("vanished row is a no-op (matches legacy zero-rows updateById)")
        void vanishedRowNoOp() {
            when(problemMapper.selectById(99L)).thenReturn(null);

            port.applyImportedUpdate(99L, "T", "D", "s", true, true);

            verify(problemMapper, never()).updateById(any(Problem.class));
        }

        @Test
        @DisplayName("null id is a no-op without touching the mapper")
        void nullIdNoOp() {
            port.applyImportedUpdate(null, "T", "D", "s", true, true);

            verify(problemMapper, never()).selectById(any());
            verify(problemMapper, never()).updateById(any(Problem.class));
        }
    }

    @Nested
    @DisplayName("applyImportedBatch()")
    class ApplyImportedBatch {

        @Test
        @DisplayName("captures one item failure and still writes later items")
        void isolatesItemFailure() {
            when(problemMapper.selectById(1L)).thenThrow(new RuntimeException("boom"));
            ProblemOwnerPort.ImportWriteRequest bad = new ProblemOwnerPort.ImportWriteRequest(
                    "0", false, 1L, "bad", "Bad", "Easy", null, null, null);
            ProblemOwnerPort.ImportWriteRequest good = new ProblemOwnerPort.ImportWriteRequest(
                    "1", true, null, "good", "Good", "Easy", null, null, null);

            List<ProblemOwnerPort.ImportWriteResult> results =
                    port.applyImportedBatch(List.of(bad, good));

            assertThat(results).extracting(ProblemOwnerPort.ImportWriteResult::key)
                    .containsExactly("0", "1");
            assertThat(results.get(0).success()).isFalse();
            assertThat(results.get(0).error()).isEqualTo("boom");
            assertThat(results.get(1).success()).isTrue();
            verify(problemMapper).insert(any(Problem.class));
        }
    }
}
