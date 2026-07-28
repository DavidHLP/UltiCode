package com.ulticode.app.dubbo.provider;

import com.ulticode.app.api.command.CreateProblemCommand;
import com.ulticode.app.api.command.PublishProblemCommand;
import com.ulticode.app.api.command.UpdateProblemCommand;
import com.ulticode.app.api.command.ActorDelegation;
import com.ulticode.app.api.dto.ProblemAdminViewDTO;
import com.ulticode.app.api.error.AppErrorCode;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.common.tracing.IdMetadata;
import com.ulticode.common.tracing.TraceMetadata;
import com.ulticode.modules.problem.dto.CreateProblemDTO;
import com.ulticode.modules.problem.dto.ProblemVO;
import com.ulticode.modules.problem.dto.UpdateProblemDTO;
import com.ulticode.modules.problem.entity.Problem;
import com.ulticode.modules.problem.service.ProblemService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P4-CUTOVER-001: unit test for {@link ProblemAdministrationProvider}.
 *
 * <p>Pins the Dubbo contract → domain-service mapping:
 * <ul>
 *   <li>createProblem maps CreateProblemCommand → CreateProblemDTO, returns
 *       ProblemAdminViewDTO with the entity's version.</li>
 *   <li>updateProblem maps UpdateProblemCommand → UpdateProblemDTO.</li>
 *   <li>publishProblem delegates to publish/unpublish based on the boolean.</li>
 *   <li>BusinessException maps to the correct AppErrorCode.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ProblemAdministrationProvider")
class ProblemAdministrationProviderTest {

    @Mock
    private ProblemService problemService;

    private ProblemAdministrationProvider provider;

    @BeforeEach
    void setUp() {
        provider = new ProblemAdministrationProvider(problemService);
    }

    private static ActorDelegation adminActor() {
        return new ActorDelegation("ADMIN", "admin-1", "admin-1", "test");
    }

    private static CreateProblemCommand createCmd(String slug, String title) {
        return new CreateProblemCommand(
                java.util.UUID.randomUUID().toString(),
                IdMetadata.mint(),
                adminActor(),
                TraceMetadata.EMPTY,
                slug, title, "admin-1");
    }

    @Nested
    @DisplayName("createProblem()")
    class Create {

        @Test
        @DisplayName("maps command to DTO and returns admin view with entity version")
        void createsAndReturnsVersion() {
            ProblemVO vo = new ProblemVO();
            vo.setId(42L);
            vo.setSlug("two-sum");
            vo.setTitle("Two Sum");
            vo.setStatus("todo");
            Problem entity = new Problem();
            entity.setId(42L);
            entity.setVersion(3);
            when(problemService.createProblem(any(CreateProblemDTO.class))).thenReturn(vo);
            when(problemService.findById(42L)).thenReturn(Optional.of(entity));

            RpcResult<ProblemAdminViewDTO> result = provider.createProblem(
                    createCmd("two-sum", "Two Sum"));

            assertThat(result.success()).isTrue();
            ProblemAdminViewDTO dto = result.data();
            assertThat(dto.slug()).isEqualTo("two-sum");
            assertThat(dto.title()).isEqualTo("Two Sum");
            assertThat(dto.version()).isEqualTo(3L);
            assertThat(dto.status()).isEqualTo("todo");
        }

        @Test
        @DisplayName("maps BusinessException(PROBLEM_NOT_FOUND) to CONTENT_NOT_FOUND")
        void mapsNotFound() {
            when(problemService.createProblem(any()))
                    .thenThrow(new BusinessException(ErrorCode.PROBLEM_NOT_FOUND, "not found"));

            RpcResult<ProblemAdminViewDTO> result = provider.createProblem(
                    createCmd("x", "X"));

            assertThat(result.success()).isFalse();
            assertThat(result.error().code()).isEqualTo(AppErrorCode.CONTENT_NOT_FOUND.code());
        }

        @Test
        @DisplayName("maps BusinessException(CONFLICT) to CONTENT_STATE_CONFLICT")
        void mapsConflict() {
            when(problemService.createProblem(any()))
                    .thenThrow(new BusinessException(ErrorCode.CONFLICT, "dup slug"));

            RpcResult<ProblemAdminViewDTO> result = provider.createProblem(
                    createCmd("dup", "Dup"));

            assertThat(result.success()).isFalse();
            assertThat(result.error().code()).isEqualTo(AppErrorCode.CONTENT_STATE_CONFLICT.code());
        }
    }

    @Nested
    @DisplayName("publishProblem()")
    class Publish {

        @Test
        @DisplayName("publish=true delegates to publishProblem")
        void publishes() {
            when(problemService.findById(any())).thenReturn(Optional.of(new Problem()));

            provider.publishProblem(new PublishProblemCommand(
                    java.util.UUID.randomUUID().toString(),
                    IdMetadata.mint(), adminActor(), TraceMetadata.EMPTY,
                    "42", 0L, true, "go-live"));

            verify(problemService).publishProblem(42L);
        }

        @Test
        @DisplayName("publish=false delegates to unpublishProblem")
        void unpublishes() {
            provider.publishProblem(new PublishProblemCommand(
                    java.util.UUID.randomUUID().toString(),
                    IdMetadata.mint(), adminActor(), TraceMetadata.EMPTY,
                    "42", 0L, false, "rollback"));

            verify(problemService).unpublishProblem(42L);
        }

        @Test
        @DisplayName("invalid id returns CONTENT_NOT_FOUND")
        void invalidId() {
            RpcResult<Void> result = provider.publishProblem(new PublishProblemCommand(
                    java.util.UUID.randomUUID().toString(),
                    IdMetadata.mint(), adminActor(), TraceMetadata.EMPTY,
                    "not-a-number", 0L, true, "x"));

            assertThat(result.success()).isFalse();
            assertThat(result.error().code()).isEqualTo(AppErrorCode.CONTENT_NOT_FOUND.code());
        }
    }

    @Nested
    @DisplayName("updateProblem()")
    class Update {

        @Test
        @DisplayName("maps command to DTO and returns updated view")
        void updatesAndReturns() {
            ProblemVO vo = new ProblemVO();
            vo.setId(10L);
            vo.setSlug("old-slug");
            vo.setTitle("New Title");
            vo.setStatus("todo");
            Problem entity = new Problem();
            entity.setVersion(5);
            when(problemService.updateProblem(any(), any(UpdateProblemDTO.class))).thenReturn(vo);
            when(problemService.findById(10L)).thenReturn(Optional.of(entity));

            RpcResult<ProblemAdminViewDTO> result = provider.updateProblem(
                    new UpdateProblemCommand(
                            java.util.UUID.randomUUID().toString(),
                            IdMetadata.mint(), adminActor(), TraceMetadata.EMPTY,
                            "10", 0L, "New Title", "typo fix"));

            assertThat(result.success()).isTrue();
            assertThat(result.data().title()).isEqualTo("New Title");
            assertThat(result.data().version()).isEqualTo(5L);
        }
    }
}
