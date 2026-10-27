package com.ulticode.app.dubbo.provider;

import com.ulticode.common.command.ActorDelegation;
import com.ulticode.app.api.command.CreateProblemCommand;
import com.ulticode.app.api.command.DeleteProblemCommand;
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
import com.ulticode.modules.problem.service.ProblemIndexRefresher;
import com.ulticode.app.security.AdminActorAuthorizer;
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

    @Mock
    private ProblemIndexRefresher indexRefresher;
    @Mock
    private AdminActorAuthorizer actorAuthorizer;

    private ProblemAdministrationProvider provider;

    @BeforeEach
    void setUp() {
        provider = new ProblemAdministrationProvider(domainService, indexRefresher, actorAuthorizer);
        when(actorAuthorizer.isAuthorized(any())).thenReturn(true);
    }
    @Test
    @DisplayName("rejects an untrusted actor before mutating the problem owner")
    void rejectsUntrustedActor() {
        when(actorAuthorizer.isAuthorized(any())).thenReturn(false);
        CreateProblemCommand command = new CreateProblemCommand(
                UUID.randomUUID().toString(), IdMetadata.mint(), adminActor(), trace(),
                "two-sum", "Two Sum", UUID.randomUUID().toString());

        RpcResult<ProblemAdminViewDTO> result = provider.createProblem(command);

        assertThat(result.success()).isFalse();
        assertThat(result.error().code()).isEqualTo(AppErrorCode.FORBIDDEN.code());
        verify(domainService, org.mockito.Mockito.never()).createProblem(any(), any());
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

    @Test
    void completePayloadSurvivesCreateAndPartialUpdate() {
        var fields = new com.ulticode.app.api.dto.ProblemWriteFields(
                "new-slug", "Hard", true, false, true, "summary", "content", "[]", "[\"limit\"]", "[\"hint\"]",
                java.util.List.of(new com.ulticode.app.api.dto.ProblemWriteFields.Language("python", "starter")),
                java.util.List.of("Array"));
        var entity = problemEntity(42L, "new-slug", "Title", 1);
        when(domainService.createProblem(any(), eq("admin-1"))).thenReturn(entity);
        when(domainService.updateProblem(eq(42L), any(), eq("admin-1"), eq(1L))).thenReturn(entity);
        assertThat(provider.createProblem(new CreateProblemCommand(UUID.randomUUID().toString(), IdMetadata.mint(),
                adminActor(), trace(), "new-slug", "Title", "admin-1", fields)).success()).isTrue();
        var create = org.mockito.ArgumentCaptor.forClass(CreateProblemDTO.class);
        verify(domainService).createProblem(create.capture(), eq("admin-1"));
        assertThat(create.getValue().getDifficulty()).isEqualTo("Hard");
        assertThat(create.getValue().getIsPublished()).isFalse();
        assertThat(create.getValue().getContent()).isEqualTo("content");
        assertThat(create.getValue().getConstraints()).isEqualTo("[\"limit\"]");
        assertThat(create.getValue().getLanguages()).containsExactly("python");
        assertThat(create.getValue().getTags()).containsExactly("Array");
        assertThat(provider.updateProblem(new UpdateProblemCommand(UUID.randomUUID().toString(), IdMetadata.mint(),
                adminActor(), trace(), "42", 1L, null, "partial", fields)).success()).isTrue();
        var update = org.mockito.ArgumentCaptor.forClass(UpdateProblemDTO.class);
        verify(domainService).updateProblem(eq(42L), update.capture(), eq("admin-1"), eq(1L));
        assertThat(update.getValue().getTitle()).isNull();
        assertThat(update.getValue().getSlug()).isEqualTo("new-slug");
        assertThat(update.getValue().getHasSolution()).isTrue();
        assertThat(update.getValue().getSummary()).isEqualTo("summary");
        assertThat(update.getValue().getHints()).isEqualTo("[\"hint\"]");
        assertThat(update.getValue().getExamples()).isEqualTo("[]");
        assertThat(update.getValue().getLanguages().get(0).getStarterCode()).isEqualTo("starter");
    }

    @Test
    void failedCompleteCreateMarksTheOwnerTransactionForRollback() {
        var status = new org.springframework.transaction.support.SimpleTransactionStatus();
        try (var synchronization = org.mockito.Mockito.mockStatic(org.springframework.transaction.support.TransactionSynchronizationManager.class);
             var aspect = org.mockito.Mockito.mockStatic(org.springframework.transaction.interceptor.TransactionAspectSupport.class)) {
            synchronization.when(org.springframework.transaction.support.TransactionSynchronizationManager::isActualTransactionActive).thenReturn(true);
            aspect.when(org.springframework.transaction.interceptor.TransactionAspectSupport::currentTransactionStatus).thenReturn(status);
            when(domainService.createProblem(any(), any())).thenThrow(new BusinessException(BaseErrorCode.VALIDATION_FAILED, "Invalid child section"));
            var result = provider.createProblem(new CreateProblemCommand(UUID.randomUUID().toString(), IdMetadata.mint(),
                    adminActor(), trace(), "invalid-child", "Title", "admin-1"));
            assertThat(result.success()).isFalse();
            assertThat(status.isRollbackOnly()).isTrue();
            assertThat(result.error().code()).isEqualTo(AppErrorCode.BAD_REQUEST.code());
        }
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
            verify(indexRefresher).publish(entity);
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
            when(domainService.updateProblem(eq(10L), any(UpdateProblemDTO.class), eq("admin-1"), eq(1L)))
                    .thenReturn(entity);

            RpcResult<ProblemAdminViewDTO> result = provider.updateProblem(command);

            assertThat(result.success()).isTrue();
            assertThat(result.data().problemId()).isEqualTo("10");
            verify(indexRefresher).publish(entity);
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
            when(domainService.publishProblem(42L, "admin-1", 1L)).thenReturn(entity);

            RpcResult<Void> result = provider.publishProblem(command);

            assertThat(result.success()).isTrue();
            verify(domainService).publishProblem(42L, "admin-1", 1L);
            verify(indexRefresher).publish(entity);
        }

        @Test
        @DisplayName("publish=false delegates to unpublishProblem")
        void publishFalse() {
            PublishProblemCommand command = new PublishProblemCommand(
                    UUID.randomUUID().toString(), IdMetadata.mint(), adminActor(), trace(),
                    "42", 1L, false, "unpublishing");

            Problem entity = problemEntity(42L, "two-sum", "Two Sum", 1);
            when(domainService.unpublishProblem(42L, "admin-1", 1L)).thenReturn(entity);

            RpcResult<Void> result = provider.publishProblem(command);

            assertThat(result.success()).isTrue();
            verify(domainService).unpublishProblem(42L, "admin-1", 1L);
            verify(indexRefresher).publish(entity);
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

        @Test
        @DisplayName("version conflict from domain maps to VERSION_CONFLICT")
        void versionConflict() {
            PublishProblemCommand command = new PublishProblemCommand(
                    UUID.randomUUID().toString(), IdMetadata.mint(), adminActor(), trace(),
                    "42", 7L, true, "publishing");
            when(domainService.publishProblem(42L, "admin-1", 7L))
                    .thenThrow(new BusinessException(AppErrorCode.VERSION_CONFLICT));

            RpcResult<Void> result = provider.publishProblem(command);

            assertThat(result.success()).isFalse();
            assertThat(result.error().code()).isEqualTo(AppErrorCode.VERSION_CONFLICT.code());
        }
    }

    @Nested
    @DisplayName("deleteProblem")
    class Delete {

        @Test
        @DisplayName("forwards expected version to the domain")
        void forwardsExpectedVersion() {
            DeleteProblemCommand command = new DeleteProblemCommand(
                    UUID.randomUUID().toString(), IdMetadata.mint(), adminActor(), trace(),
                    "42", 3L, "deleting");
            Problem existing = problemEntity(42L, "two-sum", "Two Sum", 3);
            when(domainService.findById(42L)).thenReturn(java.util.Optional.of(existing));

            RpcResult<Void> result = provider.deleteProblem(command);

            assertThat(result.success()).isTrue();
            verify(domainService).deleteProblem(42L, "admin-1", 3L);
            verify(indexRefresher).publish(existing);
        }
    }
}
