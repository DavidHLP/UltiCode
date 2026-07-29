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
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProblemAdministrationDomainServiceTest {

    @Mock
    private ProblemWritePort writePort;

    @Mock
    private ProblemDetailDomainPort detailPort;

    @Mock
    private ProblemVersionPort versionPort;

    private Clock fixedClock;
    private ProblemAdministrationDomainServiceImpl service;

    @BeforeEach
    void setUp() {
        fixedClock = Clock.fixed(Instant.parse("2026-07-29T10:00:00Z"), ZoneId.of("UTC"));
        service = new ProblemAdministrationDomainServiceImpl(writePort, detailPort, versionPort, fixedClock);
    }

    @Nested
    @DisplayName("createProblem")
    class CreateProblem {

        @Test
        @DisplayName("successful creation sets defaults, inserts entity, and creates initial version")
        void success() {
            CreateProblemDTO dto = new CreateProblemDTO();
            dto.setSlug("two-sum");
            dto.setTitle("Two Sum");
            dto.setDifficulty("Easy");

            when(writePort.selectBySlug("two-sum")).thenReturn(null);

            doAnswer(invocation -> {
                Problem p = invocation.getArgument(0);
                p.setId(42L);
                return null;
            }).when(writePort).insert(any(Problem.class));

            Problem created = service.createProblem(dto, "user-123");

            assertThat(created.getId()).isEqualTo(42L);
            assertThat(created.getSlug()).isEqualTo("two-sum");
            assertThat(created.getTitle()).isEqualTo("Two Sum");
            assertThat(created.getDifficulty()).isEqualTo("Easy");
            assertThat(created.getIsPublished()).isTrue();
            assertThat(created.getPublishedBy()).isEqualTo("user-123");

            verify(versionPort).createInitialVersion(42L, "user-123");
        }

        @Test
        @DisplayName("duplicate slug throws CONFLICT BusinessException")
        void duplicateSlug() {
            CreateProblemDTO dto = new CreateProblemDTO();
            dto.setSlug("existing");

            when(writePort.selectBySlug("existing")).thenReturn(new Problem());

            assertThatThrownBy(() -> service.createProblem(dto, "user-123"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(BaseErrorCode.CONFLICT);

            verify(writePort, never()).insert(any());
        }
    }

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

            Problem updated = service.updateProblem(10L, dto, "admin-1");

            assertThat(updated.getTitle()).isEqualTo("New Title");
            verify(writePort).updateById(existing);
            verify(detailPort).applyDetailUpdate(eq(10L), eq(existing), eq(dto));
            verify(versionPort).createVersion(10L, "UPDATE", null, "admin-1");
        }
    }
}
