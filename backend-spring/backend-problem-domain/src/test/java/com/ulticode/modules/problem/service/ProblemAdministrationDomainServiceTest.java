package com.ulticode.modules.problem.service;

import com.ulticode.common.error.BaseErrorCode;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.modules.problem.dto.CreateProblemDTO;
import com.ulticode.modules.problem.dto.UpdateProblemDTO;
import com.ulticode.modules.problem.entity.Problem;
import com.ulticode.modules.problem.port.ProblemDetailDomainPort;
import com.ulticode.modules.problem.port.ProblemVersionPort;
import com.ulticode.modules.problem.port.ProblemWritePort;
import com.ulticode.modules.problem.service.impl.ProblemAdministrationDomainServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProblemAdministrationDomainServiceTest {

    @Mock private ProblemWritePort writePort;
    @Mock private ProblemDetailDomainPort detailPort;
    @Mock private ProblemVersionPort versionPort;

    private Clock fixedClock;
    private ProblemAdministrationDomainServiceImpl service;

    private static final String ACTOR_ID = "user-123";
    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-07-29T10:00:00Z"), ZoneId.of("UTC"));

    @BeforeEach
    void setUp() {
        fixedClock = FIXED_CLOCK;
        service = new ProblemAdministrationDomainServiceImpl(writePort, detailPort, versionPort, fixedClock);
    }

    // ── findById / findBySlug ───────────────────────────────────────────────

    @Nested
    @DisplayName("findById")
    class FindById {

        @Test
        @DisplayName("returns empty when id is null")
        void returnsEmptyWhenNull() {
            assertThat(service.findById(null)).isEmpty();
            verify(writePort, never()).selectById(any());
        }

        @Test
        @DisplayName("returns empty when entity not found")
        void returnsEmptyWhenNotFound() {
            when(writePort.selectById(99L)).thenReturn(null);
            assertThat(service.findById(99L)).isEmpty();
        }

        @Test
        @DisplayName("returns entity wrapped in Optional when found")
        void returnsEntityWhenFound() {
            Problem p = new Problem();
            p.setId(1L);
            p.setSlug("two-sum");
            when(writePort.selectById(1L)).thenReturn(p);
            assertThat(service.findById(1L)).hasValue(p);
        }
    }

    @Nested
    @DisplayName("findBySlug")
    class FindBySlug {

        @Test
        @DisplayName("returns empty when slug is null")
        void returnsEmptyWhenNull() {
            assertThat(service.findBySlug(null)).isEmpty();
            verify(writePort, never()).selectBySlug(any());
        }

        @Test
        @DisplayName("returns empty when slug is blank")
        void returnsEmptyWhenBlank() {
            assertThat(service.findBySlug("  ")).isEmpty();
            verify(writePort, never()).selectBySlug(any());
        }

        @Test
        @DisplayName("returns empty when entity not found")
        void returnsEmptyWhenNotFound() {
            when(writePort.selectBySlug("nonexistent")).thenReturn(null);
            assertThat(service.findBySlug("nonexistent")).isEmpty();
        }

        @Test
        @DisplayName("returns entity when found")
        void returnsEntityWhenFound() {
            Problem p = new Problem();
            p.setId(2L);
            p.setSlug("two-sum");
            when(writePort.selectBySlug("two-sum")).thenReturn(p);
            assertThat(service.findBySlug("two-sum")).hasValue(p);
        }
    }

    // ── createProblem ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("createProblem")
    class CreateProblem {

        @Test
        @DisplayName("successful creation sets defaults, publishes, inserts entity, and creates initial version")
        void success() {
            CreateProblemDTO dto = new CreateProblemDTO();
            dto.setSlug("two-sum");
            dto.setTitle("Two Sum");
            dto.setDifficulty("Easy");
            // isPublished defaults to true

            when(writePort.selectBySlug("two-sum")).thenReturn(null);

            doAnswer(invocation -> {
                Problem p = invocation.getArgument(0);
                p.setId(42L);
                return null;
            }).when(writePort).insert(any(Problem.class));

            Problem created = service.createProblem(dto, ACTOR_ID);

            assertThat(created.getId()).isEqualTo(42L);
            assertThat(created.getSlug()).isEqualTo("two-sum");
            assertThat(created.getTitle()).isEqualTo("Two Sum");
            assertThat(created.getDifficulty()).isEqualTo("Easy");
            assertThat(created.getStatus()).isEqualTo("todo");
            assertThat(created.getHasSolution()).isFalse();
            assertThat(created.getIsPublished()).isTrue();
            assertThat(created.getPublishedAt()).isNotNull();
            assertThat(created.getPublishedBy()).isEqualTo(ACTOR_ID);

            verify(writePort).insert(any(Problem.class));
            verify(versionPort).createInitialVersion(42L, ACTOR_ID);
        }

        @Test
        @DisplayName("duplicate slug throws CONFLICT BusinessException")
        void duplicateSlug() {
            CreateProblemDTO dto = new CreateProblemDTO();
            dto.setSlug("existing");

            when(writePort.selectBySlug("existing")).thenReturn(new Problem());

            assertThatThrownBy(() -> service.createProblem(dto, ACTOR_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(BaseErrorCode.CONFLICT);

            verify(writePort, never()).insert(any());
        }

        @Test
        @DisplayName("creation with isPublished=false does not set publishedAt/publishedBy")
        void notPublished() {
            CreateProblemDTO dto = new CreateProblemDTO();
            dto.setSlug("draft-problem");
            dto.setTitle("Draft");
            dto.setDifficulty("Medium");
            dto.setIsPublished(false);

            when(writePort.selectBySlug("draft-problem")).thenReturn(null);
            doAnswer(invocation -> {
                Problem p = invocation.getArgument(0);
                p.setId(10L);
                return null;
            }).when(writePort).insert(any(Problem.class));

            Problem created = service.createProblem(dto, ACTOR_ID);

            assertThat(created.getIsPublished()).isFalse();
            assertThat(created.getPublishedAt()).isNull();
            assertThat(created.getPublishedBy()).isNull();
        }
    }

    // ── updateProblem ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("updateProblem")
    class UpdateProblem {

        @Test
        @DisplayName("successful update applies changes, updates entity, calls satellite port and version port")
        void success() {
            Problem existing = new Problem();
            existing.setId(10L);
            existing.setSlug("old-slug");
            existing.setTitle("Old Title");

            when(writePort.selectById(10L)).thenReturn(existing);

            UpdateProblemDTO dto = new UpdateProblemDTO();
            dto.setTitle("New Title");

            Problem updated = service.updateProblem(10L, dto, ACTOR_ID);

            assertThat(updated.getTitle()).isEqualTo("New Title");
            verify(writePort).updateById(existing);
            verify(detailPort).applyDetailUpdate(eq(10L), eq(existing), eq(dto));
            verify(versionPort).createVersion(10L, "UPDATE", null, ACTOR_ID);
        }

        @Test
        @DisplayName("not-found id throws NOT_FOUND")
        void notFound() {
            when(writePort.selectById(99L)).thenReturn(null);

            UpdateProblemDTO dto = new UpdateProblemDTO();
            dto.setTitle("New Title");

            assertThatThrownBy(() -> service.updateProblem(99L, dto, ACTOR_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(BaseErrorCode.NOT_FOUND);

            verify(writePort, never()).updateById(any());
        }
    }

    // ── deleteProblem ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("deleteProblem")
    class DeleteProblem {

        @Test
        @DisplayName("successful delete calls deleteById")
        void success() {
            Problem existing = new Problem();
            existing.setId(5L);
            when(writePort.selectById(5L)).thenReturn(existing);

            service.deleteProblem(5L, ACTOR_ID);

            verify(writePort).deleteById(5L);
        }

        @Test
        @DisplayName("not-found id throws NOT_FOUND")
        void notFound() {
            when(writePort.selectById(99L)).thenReturn(null);

            assertThatThrownBy(() -> service.deleteProblem(99L, ACTOR_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(BaseErrorCode.NOT_FOUND);

            verify(writePort, never()).deleteById(any());
        }
    }

    // ── publishProblem ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("publishProblem")
    class PublishProblem {

        @Test
        @DisplayName("first publish sets isPublished, publishedAt, publishedBy")
        void firstPublish() {
            Problem existing = new Problem();
            existing.setId(7L);
            existing.setIsPublished(false);
            existing.setPublishedAt(null);
            existing.setPublishedBy(null);
            when(writePort.selectById(7L)).thenReturn(existing);

            Problem result = service.publishProblem(7L, ACTOR_ID);

            assertThat(result.getIsPublished()).isTrue();
            assertThat(result.getPublishedAt()).isNotNull();
            assertThat(result.getPublishedBy()).isEqualTo(ACTOR_ID);
            verify(writePort).updateById(existing);
        }

        @Test
        @DisplayName("idempotent publish does not overwrite publishedBy")
        void idempotentPublish() {
            Problem existing = new Problem();
            existing.setId(7L);
            existing.setIsPublished(true);
            existing.setPublishedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
            existing.setPublishedBy("original-actor");
            when(writePort.selectById(7L)).thenReturn(existing);

            Problem result = service.publishProblem(7L, "new-actor");

            assertThat(result.getIsPublished()).isTrue();
            assertThat(result.getPublishedAt()).isEqualTo(LocalDateTime.of(2026, 1, 1, 0, 0));
            assertThat(result.getPublishedBy()).isEqualTo("original-actor");
            verify(writePort).updateById(existing);
        }

        @Test
        @DisplayName("not-found id throws NOT_FOUND")
        void notFound() {
            when(writePort.selectById(99L)).thenReturn(null);

            assertThatThrownBy(() -> service.publishProblem(99L, ACTOR_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(BaseErrorCode.NOT_FOUND);
        }
    }

    // ── unpublishProblem ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("unpublishProblem")
    class UnpublishProblem {

        @Test
        @DisplayName("successful unpublish sets isPublished=false, leaves publishedAt/publishedBy intact")
        void success() {
            Problem existing = new Problem();
            existing.setId(7L);
            existing.setIsPublished(true);
            existing.setPublishedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
            existing.setPublishedBy("some-actor");
            when(writePort.selectById(7L)).thenReturn(existing);

            Problem result = service.unpublishProblem(7L, ACTOR_ID);

            assertThat(result.getIsPublished()).isFalse();
            assertThat(result.getPublishedAt()).isEqualTo(LocalDateTime.of(2026, 1, 1, 0, 0));
            assertThat(result.getPublishedBy()).isEqualTo("some-actor");
            verify(writePort).updateById(existing);
        }

        @Test
        @DisplayName("not-found id throws NOT_FOUND")
        void notFound() {
            when(writePort.selectById(99L)).thenReturn(null);

            assertThatThrownBy(() -> service.unpublishProblem(99L, ACTOR_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(BaseErrorCode.NOT_FOUND);
        }
    }
}
