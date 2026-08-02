package com.ulticode.modules.moderation.adapter;

import com.ulticode.app.api.dto.ContentLifecycleState;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.modules.admin.service.AdminForumService;
import com.ulticode.modules.admin.service.AdminSolutionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

/**
 * Tests for {@link LegacyContentModerationWriteAdapter}.
 * Updated for P7-INFRA-S3: adapter now implements ModerationContentActionPort
 * with deleteContent(contentType, contentId) instead of apply(command).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("LegacyContentModerationWriteAdapter")
class LegacyContentModerationWriteAdapterTest {

    @Mock private AdminForumService forumService;
    @Mock private AdminSolutionService solutionService;
    @InjectMocks private LegacyContentModerationWriteAdapter adapter;

    @Nested
    @DisplayName("deleteContent")
    class DeleteContent {

        @Test
        @DisplayName("deletes forum post and returns DELETED state")
        void deletesForumPost() {
            ContentLifecycleState result = adapter.deleteContent("forum_post", "post-123");
            assertThat(result).isEqualTo(ContentLifecycleState.DELETED);
            verify(forumService).deletePost("post-123");
        }

        @Test
        @DisplayName("deletes solution and returns DELETED state")
        void deletesSolution() {
            ContentLifecycleState result = adapter.deleteContent("solution", "sol-456");
            assertThat(result).isEqualTo(ContentLifecycleState.DELETED);
            verify(solutionService).deleteSolution("sol-456");
        }

        @Test
        @DisplayName("throws on unknown content type")
        void throwsOnUnknownType() {
            assertThatThrownBy(() -> adapter.deleteContent("unknown_type", "id-1"))
                    .isInstanceOf(BusinessException.class);
        }
    }
}
