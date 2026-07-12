package com.ulticode.modules.admin.service.impl;

import com.ulticode.modules.admin.dto.problem.ImportProblemItemDTO;
import com.ulticode.modules.admin.dto.problem.ImportProblemsRequestDTO;
import com.ulticode.modules.admin.dto.problem.ImportProblemsResponseDTO;
import com.ulticode.modules.admin.port.AdminProblemPort;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ProblemImportServiceImpl} &mdash; the problem
 * batch-import module lifted out of the legacy inline
 * {@code AdminProblemServiceImpl#importProblems} per the admin import
 * deepening.
 *
 * <p>Covers every invariant the module owns with a pure mock-mapper graph
 * and no database: conflict-policy resolution (skip / update / create_new
 * / unknown), create identity and defaults, partial update of existing
 * rows, the wall-clock slug suffix on create_new, per-row failure
 * isolation, and result accounting.
 *
 * <p>Behavior is pinned exactly against the new import seam so the
 * legacy contract is preserved.
 *
 * @author ulticode
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ProblemImportServiceImpl (problem batch import)")
class ProblemImportServiceImplTest {

    @Mock private ProblemMapper problemMapper;
    @Mock private AdminProblemPort problemPort;

    private ProblemImportServiceImpl importService;

    @BeforeEach
    void setUp() {
        importService = new ProblemImportServiceImpl(problemMapper, problemPort);
    }

    private ImportProblemItemDTO item(String slug) {
        ImportProblemItemDTO item = new ImportProblemItemDTO();
        item.setSlug(slug);
        item.setTitle("Title " + slug);
        item.setDifficulty("Easy");
        return item;
    }

    private ImportProblemItemDTO itemWith(String slug, String title, String difficulty,
                                          String status, Boolean premium, Boolean published) {
        ImportProblemItemDTO item = new ImportProblemItemDTO();
        item.setSlug(slug);
        item.setTitle(title);
        item.setDifficulty(difficulty);
        item.setStatus(status);
        item.setIsPremium(premium);
        item.setIsPublished(published);
        return item;
    }

    private ImportProblemsRequestDTO request(List<ImportProblemItemDTO> items, String onConflict) {
        ImportProblemsRequestDTO request = new ImportProblemsRequestDTO();
        request.setProblems(items);
        request.setOnConflict(onConflict);
        return request;
    }

    private ImportProblemsResponseDTO.ImportResultItem resultFor(
            ImportProblemsResponseDTO response, String slug) {
        return response.getResults().stream()
                .filter(r -> slug.equals(r.getSlug()))
                .findFirst()
                .orElseThrow();
    }

    @Nested
    @DisplayName("new problems (no slug collision)")
    class NewProblem {

        @Test
        @DisplayName("inserts with import defaults and reports created")
        void insertsNewProblemWithDefaults() {
            ImportProblemItemDTO item = itemWith("two-sum", "Two Sum", "Easy", null, null, null);
            when(problemPort.findBySlug("two-sum")).thenReturn(Optional.empty());

            ImportProblemsResponseDTO response = importService.importProblems(request(List.of(item), "skip"));

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

            assertThat(response.getTotal()).isEqualTo(1);
            assertThat(response.getCreated()).isEqualTo(1);
            assertThat(response.getUpdated()).isZero();
            assertThat(response.getSkipped()).isZero();
            assertThat(response.getFailed()).isZero();
            ImportProblemsResponseDTO.ImportResultItem r = resultFor(response, "two-sum");
            assertThat(r.isSuccess()).isTrue();
            assertThat(r.getAction()).isEqualTo("created");
            assertThat(r.getError()).isNull();
            verify(problemMapper, never()).updateById(any(Problem.class));
        }

        @Test
        @DisplayName("honors explicit status / premium / published flags")
        void honorsExplicitFlags() {
            ImportProblemItemDTO item = itemWith("three-sum", "Three Sum", "Hard", "solved", true, true);
            when(problemPort.findBySlug("three-sum")).thenReturn(Optional.empty());

            ImportProblemsResponseDTO response = importService.importProblems(request(List.of(item), "skip"));

            ArgumentCaptor<Problem> captor = ArgumentCaptor.forClass(Problem.class);
            verify(problemMapper).insert(captor.capture());
            Problem inserted = captor.getValue();
            assertThat(inserted.getStatus()).isEqualTo("solved");
            assertThat(inserted.getIsPremium()).isTrue();
            assertThat(inserted.getIsPublished()).isTrue();
            assertThat(response.getCreated()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("conflict policy")
    class ConflictPolicy {

        @Test
        @DisplayName("skip: leaves the row untouched and reports skipped")
        void skipConflict() {
            ImportProblemItemDTO item = item("dup");
            Problem existing = new Problem();
            existing.setSlug("dup");
            existing.setTitle("Old");
            when(problemPort.findBySlug("dup")).thenReturn(Optional.of(existing));

            ImportProblemsResponseDTO response = importService.importProblems(request(List.of(item), "skip"));

            verify(problemMapper, never()).insert(any(Problem.class));
            verify(problemMapper, never()).updateById(any(Problem.class));
            assertThat(existing.getTitle()).isEqualTo("Old");
            assertThat(response.getSkipped()).isEqualTo(1);
            assertThat(resultFor(response, "dup").getAction()).isEqualTo("skipped");
        }

        @Test
        @DisplayName("update: applies non-null DTO fields and reports updated")
        void updateConflict() {
            ImportProblemItemDTO item = itemWith("dup", "New Title", "Hard", "solved", true, null);
            Problem existing = new Problem();
            existing.setSlug("dup");
            existing.setTitle("Old");
            existing.setDifficulty("Easy");
            existing.setStatus("todo");
            existing.setIsPremium(false);
            existing.setIsPublished(false);
            when(problemPort.findBySlug("dup")).thenReturn(Optional.of(existing));

            ImportProblemsResponseDTO response = importService.importProblems(request(List.of(item), "update"));

            verify(problemMapper).updateById(existing);
            verify(problemMapper, never()).insert(any(Problem.class));
            assertThat(existing.getTitle()).isEqualTo("New Title");
            assertThat(existing.getDifficulty()).isEqualTo("Hard");
            assertThat(existing.getStatus()).isEqualTo("solved");
            assertThat(existing.getIsPremium()).isTrue();
            // null DTO field must not overwrite the existing value
            assertThat(existing.getIsPublished()).isFalse();
            assertThat(response.getUpdated()).isEqualTo(1);
            assertThat(resultFor(response, "dup").getAction()).isEqualTo("updated");
        }

        @Test
        @DisplayName("create_new: mints a slug+wall-clock suffix and reports created")
        void createNewConflict() {
            ImportProblemItemDTO item = item("dup");
            Problem existing = new Problem();
            existing.setSlug("dup");
            when(problemPort.findBySlug("dup")).thenReturn(Optional.of(existing));

            ImportProblemsResponseDTO response = importService.importProblems(request(List.of(item), "create_new"));

            ArgumentCaptor<Problem> captor = ArgumentCaptor.forClass(Problem.class);
            verify(problemMapper).insert(captor.capture());
            Problem inserted = captor.getValue();
            assertThat(inserted.getSlug()).startsWith("dup-");
            assertThat(inserted.getSlug()).isNotEqualTo("dup");
            verify(problemMapper, never()).updateById(any(Problem.class));
            assertThat(response.getCreated()).isEqualTo(1);
            assertThat(resultFor(response, "dup").getAction()).isEqualTo("created");
        }

        @Test
        @DisplayName("unknown policy folds to skip (preserves legacy default branch)")
        void unknownPolicyFoldsToSkip() {
            ImportProblemItemDTO item = item("dup");
            Problem existing = new Problem();
            existing.setSlug("dup");
            when(problemPort.findBySlug("dup")).thenReturn(Optional.of(existing));

            ImportProblemsResponseDTO response = importService.importProblems(request(List.of(item), "bogus"));

            verify(problemMapper, never()).insert(any(Problem.class));
            verify(problemMapper, never()).updateById(any(Problem.class));
            assertThat(response.getSkipped()).isEqualTo(1);
            assertThat(resultFor(response, "dup").getAction()).isEqualTo("skipped");
        }
    }

    @Nested
    @DisplayName("failure isolation and accounting")
    class FailureIsolation {

        @Test
        @DisplayName("a failing row is counted as failed and the batch continues")
        void failingRowIsIsolated() {
            ImportProblemItemDTO ok = item("ok");
            ImportProblemItemDTO bad = item("bad");
            when(problemPort.findBySlug("ok")).thenReturn(Optional.empty());
            when(problemPort.findBySlug("bad")).thenThrow(new RuntimeException("boom"));

            ImportProblemsResponseDTO response =
                    importService.importProblems(request(List.of(ok, bad), "skip"));

            assertThat(response.getTotal()).isEqualTo(2);
            assertThat(response.getCreated()).isEqualTo(1);
            assertThat(response.getFailed()).isEqualTo(1);

            ImportProblemsResponseDTO.ImportResultItem badResult = resultFor(response, "bad");
            assertThat(badResult.isSuccess()).isFalse();
            assertThat(badResult.getError()).isEqualTo("boom");
            assertThat(badResult.getAction()).isNull();
        }
    }
}
