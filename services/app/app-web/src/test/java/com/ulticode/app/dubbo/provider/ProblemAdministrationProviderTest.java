package com.ulticode.app.dubbo.provider;

import com.ulticode.app.api.command.ActorDelegation;
import com.ulticode.app.api.command.CreateProblemCommand;
import com.ulticode.app.api.command.PublishProblemCommand;
import com.ulticode.app.api.command.UpdateProblemCommand;
import com.ulticode.app.api.dto.ProblemAdminViewDTO;
import com.ulticode.app.api.error.AppErrorCode;
import com.ulticode.common.error.BaseErrorCode;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.common.tracing.IdMetadata;
import com.ulticode.common.tracing.TraceMetadata;
import com.ulticode.modules.problem.dto.CreateProblemDTO;
import com.ulticode.modules.problem.dto.UpdateProblemDTO;
import com.ulticode.modules.problem.entity.Problem;
import com.ulticode.modules.problem.service.ProblemAdministrationDomainService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ProblemAdministrationProvider")
class ProblemAdministrationProviderTest {

    @Mock
    private ProblemAdministrationDomainService domainService;

    private ProblemAdministrationProvider provider;

    @BeforeEach
    void setUp() {
        provider = new ProblemAdministrationProvider(domainService);
    }

    private static ActorDelegation adminActor() {
        return new ActorDelegation("ADMIN", "admin-1", null, null);
    }

    private static Problem problemEntity(Long id, String slug, String title, int version) {
        Problem p = new Problem();
        p.setId(id);
        p.setSlug(slug);
        p.setTitle(title);
        p.setVersion(version);
        p.setStatus("todo");
        return p;
    }

    private static TraceMetadata trace() {
        return new TraceMetadata("t-12345", null, null, null);
    }

    @Nested
    @DisplayName("createProblem")
    class Create {

        @Test
        @DisplayName("successful creation returns admin view with entity details")
        void success() {
            CreateProblemCommand command = new CreateProblemCommand(
                    UUID.randomUUID().toString(), IdMetadata.mint(), adminActor(), trace(),
                    "two-sum", "Two Sum", UUID.randomUUID().toString());

            Problem entity = problemEntity(42L, "two-sum", "Two Sum", 1);
            when(domainService.createProblem(any(CreateProblemDTO.class), eq("admin-1")))
                    .thenReturn(entity);

            RpcResult<ProblemAdminViewDTO> result = provider.createProblem(command);

            assertThat(result.success()).isTrue();
            ProblemAdminViewDTO view = result.data();
            assertThat(view.problemId()).isEqualTo("42");
            assertThat(view.slug()).isEqualTo("two-sum");
            assertThat(view.title()).isEqualTo("Two Sum");
            assertThat(view.version()).isEqualTo(1L);
        }

        @Test
        @DisplayName("domain conflict exception maps to CONTENT_STATE_CONFLICT")
        void conflict() {
            CreateProblemCommand command = new CreateProblemCommand(
                    UUID.randomUUID().toString(), IdMetadata.mint(), adminActor(), trace(),
                    "dup", "Duplicate", UUID.randomUUID().toString());

            when(domainService.createProblem(any(CreateProblemDTO.class), any()))
                    .thenThrow(new BusinessException(BaseErrorCode.CONFLICT, "Slug duplicate"));

            RpcResult<ProblemAdminViewDTO> result = provider.createProblem(command);

            assertThat(result.success()).isFalse();
            assertThat(result.error().code()).isEqualTo(AppErrorCode.CONTENT_STATE_CONFLICT.code());
        }
    }

    @Nested
    @DisplayName("updateProblem")
    class Update {

        @Test
        @DisplayName("successful update returns admin view")
        void success() {
            UpdateProblemCommand command = new UpdateProblemCommand(
                    UUID.randomUUID().toString(), IdMetadata.mint(), adminActor(), trace(),
                    "10", 1L, "New Title", "rationale");

            Problem entity = problemEntity(10L, "two-sum", "New Title", 2);
            when(domainService.updateProblem(eq(10L), any(UpdateProblemDTO.class), eq("admin-1")))
                    .thenReturn(entity);

            RpcResult<ProblemAdminViewDTO> result = provider.updateProblem(command);

            assertThat(result.success()).isTrue();
            assertThat(result.data().problemId()).isEqualTo("10");
        }
    }

    @Nested
    @DisplayName("publishProblem")
    class Publish {

        @Test
        @DisplayName("publish=true delegates to publishProblem")
        void publishTrue() {
            PublishProblemCommand command = new PublishProblemCommand(
                    UUID.randomUUID().toString(), IdMetadata.mint(), adminActor(), trace(),
                    "42", 1L, true, "publishing");

            Problem entity = problemEntity(42L, "two-sum", "Two Sum", 1);
            when(domainService.publishProblem(42L, "admin-1")).thenReturn(entity);

            RpcResult<Void> result = provider.publishProblem(command);

            assertThat(result.success()).isTrue();
            verify(domainService).publishProblem(42L, "admin-1");
        }

        @Test
        @DisplayName("publish=false delegates to unpublishProblem")
        void publishFalse() {
            PublishProblemCommand command = new PublishProblemCommand(
                    UUID.randomUUID().toString(), IdMetadata.mint(), adminActor(), trace(),
                    "42", 1L, false, "unpublishing");

            Problem entity = problemEntity(42L, "two-sum", "Two Sum", 1);
            when(domainService.unpublishProblem(42L, "admin-1")).thenReturn(entity);

            RpcResult<Void> result = provider.publishProblem(command);

            assertThat(result.success()).isTrue();
            verify(domainService).unpublishProblem(42L, "admin-1");
        }

        @Test
        @DisplayName("invalid problemId returns CONTENT_NOT_FOUND failure")
        void invalidId() {
            PublishProblemCommand command = new PublishProblemCommand(
                    UUID.randomUUID().toString(), IdMetadata.mint(), adminActor(), trace(),
                    "not-a-number", 1L, true, "publishing");

            RpcResult<Void> result = provider.publishProblem(command);

            assertThat(result.success()).isFalse();
            assertThat(result.error().code()).isEqualTo(AppErrorCode.CONTENT_NOT_FOUND.code());
        }
    }
}
