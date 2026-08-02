package com.ulticode.modules.moderation.adapter;

import com.ulticode.app.api.command.ActorDelegation;
import com.ulticode.app.api.command.ApplyModerationCommand;
import com.ulticode.app.api.command.ApplyModerationCommand.ModerationAction;
import com.ulticode.app.api.dto.ContentLifecycleState;
import com.ulticode.app.api.dto.ModerationApplyResultDTO;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.exception.BusinessException;
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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("LegacyContentModerationWriteAdapter")
class LegacyContentModerationWriteAdapterTest {

    @Mock
    private AdminForumService forumService;

    @Mock
    private AdminSolutionService solutionService;

    private LegacyContentModerationWriteAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new LegacyContentModerationWriteAdapter(forumService, solutionService);
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
        @DisplayName("forum_post DELETE delegates to AdminForumService.deletePost")
        void deleteForumPost() {
            ModerationApplyResultDTO result = adapter.apply(cmd("forum_post", ModerationAction.DELETE));
            assertThat(result.appliedAction()).isEqualTo(ModerationAction.DELETE);
            assertThat(result.newContentState()).isEqualTo(ContentLifecycleState.DELETED);
            verify(forumService).deletePost("content-1");
            verifyNoInteractions(solutionService);
        }

        @Test
        @DisplayName("solution DELETE delegates to AdminSolutionService.deleteSolution")
        void deleteSolution() {
            ModerationApplyResultDTO result = adapter.apply(cmd("solution", ModerationAction.DELETE));
            assertThat(result.appliedAction()).isEqualTo(ModerationAction.DELETE);
            assertThat(result.newContentState()).isEqualTo(ContentLifecycleState.DELETED);
            verify(solutionService).deleteSolution("content-1");
            verifyNoInteractions(forumService);
        }
    }

    @Nested
    @DisplayName("Unsupported actions and types")
    class UnsupportedCases {

        @Test
        @DisplayName("HIDE action throws BusinessException(BAD_REQUEST)")
        void hideNotSupported() {
            assertThatThrownBy(() -> adapter.apply(cmd("forum_post", ModerationAction.HIDE)))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.BAD_REQUEST);
        }

        @Test
        @DisplayName("Unknown content type throws BusinessException(BAD_REQUEST)")
        void unknownType() {
            assertThatThrownBy(() -> adapter.apply(cmd("unknown_type", ModerationAction.DELETE)))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.BAD_REQUEST);
        }
    }
}
