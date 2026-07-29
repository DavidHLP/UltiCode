package com.ulticode.app.dubbo.provider;

import com.ulticode.app.api.command.ActorDelegation;
import com.ulticode.app.api.command.ApplyModerationCommand;
import com.ulticode.app.api.command.ApplyModerationCommand.ModerationAction;
import com.ulticode.app.api.dto.ModerationApplyResultDTO;
import com.ulticode.app.api.error.AppErrorCode;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.common.tracing.IdMetadata;
import com.ulticode.common.tracing.TraceMetadata;
import com.ulticode.modules.admin.service.AdminForumService;
import com.ulticode.modules.admin.service.AdminSolutionService;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ContentModerationProvider")
class ContentModerationProviderTest {

    @Mock private AdminForumService forumService;
    @Mock private AdminSolutionService solutionService;
    private ContentModerationProvider provider;

    @BeforeEach
    void setUp() { provider = new ContentModerationProvider(forumService, solutionService); }

    private static ActorDelegation actor() {
        return new ActorDelegation("ADMIN", "admin-1", "admin-1", "test");
    }

    private ApplyModerationCommand cmd(String contentType, ModerationAction action) {
        return new ApplyModerationCommand(
                UUID.randomUUID().toString(), IdMetadata.mint(), actor(), TraceMetadata.EMPTY,
                UUID.randomUUID().toString(), "content-1", contentType, action, "test rationale");
    }

    @Nested @DisplayName("DELETE action")
    class DeleteAction {
        @Test @DisplayName("forum_post DELETE delegates to AdminForumService.deletePost")
        void deleteForumPost() {
            RpcResult<ModerationApplyResultDTO> result = provider.apply(cmd("forum_post", ModerationAction.DELETE));
            assertThat(result.success()).isTrue();
            assertThat(result.data().appliedAction()).isEqualTo(ModerationAction.DELETE);
            assertThat(result.data().newContentState())
                    .isEqualTo(com.ulticode.app.api.dto.ContentLifecycleState.DELETED);
            verify(forumService).deletePost("content-1");
            verifyNoInteractions(solutionService);
        }

        @Test @DisplayName("solution DELETE delegates to AdminSolutionService.deleteSolution")
        void deleteSolution() {
            RpcResult<ModerationApplyResultDTO> result = provider.apply(cmd("solution", ModerationAction.DELETE));
            assertThat(result.success()).isTrue();
            assertThat(result.data().newContentState())
                    .isEqualTo(com.ulticode.app.api.dto.ContentLifecycleState.DELETED);
            verify(solutionService).deleteSolution("content-1");
            verifyNoInteractions(forumService);
        }
    }

    @Nested @DisplayName("Unsupported actions")
    class UnsupportedActions {
        @Test @DisplayName("HIDE returns CONTENT_STATE_CONFLICT")
        void hideNotSupported() {
            RpcResult<ModerationApplyResultDTO> result = provider.apply(cmd("forum_post", ModerationAction.HIDE));
            assertThat(result.success()).isFalse();
            assertThat(result.error().code()).isEqualTo(AppErrorCode.CONTENT_STATE_CONFLICT.code());
        }

        @Test @DisplayName("RESTORE returns CONTENT_STATE_CONFLICT")
        void restoreNotSupported() {
            RpcResult<ModerationApplyResultDTO> result = provider.apply(cmd("solution", ModerationAction.RESTORE));
            assertThat(result.success()).isFalse();
            assertThat(result.error().code()).isEqualTo(AppErrorCode.CONTENT_STATE_CONFLICT.code());
        }

        @Test @DisplayName("UNDELETE returns CONTENT_STATE_CONFLICT")
        void undeleteNotSupported() {
            RpcResult<ModerationApplyResultDTO> result = provider.apply(cmd("forum_post", ModerationAction.UNDELETE));
            assertThat(result.success()).isFalse();
            assertThat(result.error().code()).isEqualTo(AppErrorCode.CONTENT_STATE_CONFLICT.code());
        }
    }

    @Nested @DisplayName("Unknown content type")
    class UnknownType {
        @Test @DisplayName("unknown contentType returns CONTENT_STATE_CONFLICT")
        void unknownType() {
            RpcResult<ModerationApplyResultDTO> result = provider.apply(cmd("unknown_type", ModerationAction.DELETE));
            assertThat(result.success()).isFalse();
            assertThat(result.error().code()).isEqualTo(AppErrorCode.CONTENT_STATE_CONFLICT.code());
        }
    }

    @Nested @DisplayName("Error mapping")
    class ErrorMapping {
        @Test @DisplayName("BusinessException(NOT_FOUND) maps to CONTENT_NOT_FOUND")
        void mapsNotFound() {
            org.mockito.Mockito.doThrow(new BusinessException(ErrorCode.NOT_FOUND, "not found"))
                    .when(forumService).deletePost(anyString());
            RpcResult<ModerationApplyResultDTO> result = provider.apply(cmd("forum_post", ModerationAction.DELETE));
            assertThat(result.success()).isFalse();
            assertThat(result.error().code()).isEqualTo(AppErrorCode.CONTENT_NOT_FOUND.code());
        }
    }
}
