package com.ulticode.app.dubbo.provider;

import com.ulticode.app.api.command.ActorDelegation;
import com.ulticode.app.api.command.ApplyModerationCommand;
import com.ulticode.app.api.command.ApplyModerationCommand.ModerationAction;
import com.ulticode.app.api.dto.ContentLifecycleState;
import com.ulticode.app.api.dto.ModerationApplyResultDTO;
import com.ulticode.app.api.error.AppErrorCode;
import com.ulticode.common.error.BaseErrorCode;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.common.tracing.IdMetadata;
import com.ulticode.common.tracing.TraceMetadata;
import com.ulticode.modules.moderation.service.ContentModerationDomainService;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ContentModerationProvider")
class ContentModerationProviderTest {

    @Mock
    private ContentModerationDomainService domainService;

    private ContentModerationProvider provider;

    @BeforeEach
    void setUp() {
        provider = new ContentModerationProvider(domainService);
    }

    private static ActorDelegation actor() {
        return new ActorDelegation("ADMIN", "admin-1", "admin-1", "test");
    }

    private ApplyModerationCommand cmd(String contentType, ModerationAction action) {
        return new ApplyModerationCommand(
                UUID.randomUUID().toString(), IdMetadata.mint(), actor(), TraceMetadata.EMPTY,
                UUID.randomUUID().toString(), "content-1", contentType, action, "test rationale");
    }

    @Nested
    @DisplayName("DELETE action")
    class DeleteAction {

        @Test
        @DisplayName("forum_post DELETE delegates to domainService")
        void deleteForumPost() {
            var expected = new ModerationApplyResultDTO(
                    "case-1", "content-1", ModerationAction.DELETE, ContentLifecycleState.DELETED);
            when(domainService.apply(any())).thenReturn(expected);

            ApplyModerationCommand command = cmd("forum_post", ModerationAction.DELETE);
            RpcResult<ModerationApplyResultDTO> result = provider.apply(command);

            assertThat(result.success()).isTrue();
            assertThat(result.data().appliedAction()).isEqualTo(ModerationAction.DELETE);
            assertThat(result.data().newContentState()).isEqualTo(ContentLifecycleState.DELETED);
            verify(domainService).apply(command);
        }

        @Test
        @DisplayName("solution DELETE delegates to domainService")
        void deleteSolution() {
            var expected = new ModerationApplyResultDTO(
                    "case-1", "content-1", ModerationAction.DELETE, ContentLifecycleState.DELETED);
            when(domainService.apply(any())).thenReturn(expected);

            ApplyModerationCommand command = cmd("solution", ModerationAction.DELETE);
            RpcResult<ModerationApplyResultDTO> result = provider.apply(command);

            assertThat(result.success()).isTrue();
            assertThat(result.data().newContentState()).isEqualTo(ContentLifecycleState.DELETED);
            verify(domainService).apply(command);
        }
    }

    @Nested
    @DisplayName("Error mapping")
    class ErrorMapping {

        @Test
        @DisplayName("BusinessException(NOT_FOUND) maps to CONTENT_NOT_FOUND")
        void mapsNotFound() {
            when(domainService.apply(any()))
                    .thenThrow(new BusinessException(BaseErrorCode.NOT_FOUND, "not found"));

            RpcResult<ModerationApplyResultDTO> result = provider.apply(cmd("forum_post", ModerationAction.DELETE));
            assertThat(result.success()).isFalse();
            assertThat(result.error().code()).isEqualTo(AppErrorCode.CONTENT_NOT_FOUND.code());
        }

        @Test
        @DisplayName("BusinessException(BAD_REQUEST) maps to CONTENT_STATE_CONFLICT")
        void mapsBadRequest() {
            when(domainService.apply(any()))
                    .thenThrow(new BusinessException(BaseErrorCode.BAD_REQUEST, "unsupported action"));

            RpcResult<ModerationApplyResultDTO> result = provider.apply(cmd("forum_post", ModerationAction.HIDE));
            assertThat(result.success()).isFalse();
            assertThat(result.error().code()).isEqualTo(AppErrorCode.CONTENT_STATE_CONFLICT.code());
        }
    }
}
