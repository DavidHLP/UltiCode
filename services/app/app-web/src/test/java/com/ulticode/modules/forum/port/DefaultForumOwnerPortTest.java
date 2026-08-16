package com.ulticode.modules.forum.port;

import com.ulticode.common.error.BaseErrorCode;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.modules.forum.entity.ForumPost;
import com.ulticode.modules.forum.mapper.ForumPostMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultForumOwnerPortTest {

    @Mock
    private ForumPostMapper forumPostMapper;

    @Mock
    private com.ulticode.modules.search.source.SearchDocumentChangedPublisher searchPublisher;

    private DefaultForumOwnerPort forumOwnerPort;

    @BeforeEach
    void setUp() {
        forumOwnerPort = new DefaultForumOwnerPort(forumPostMapper, searchPublisher);
    }

    private ForumPost createPost(String id, String userId) {
        ForumPost post = new ForumPost();
        post.setId(id);
        post.setUserId(userId);
        return post;
    }

    @Nested
    @DisplayName("flagPost tests")
    class FlagPostTests {

        @Test
        @DisplayName("flagPost sets isFlagged=true and stores reason")
        void flagPost_setsFlaggedAndReason() {
            ForumPost post = createPost("p-1", "u-1");
            LocalDateTime flaggedAt = LocalDateTime.now();
            when(forumPostMapper.selectByIdForUpdateIgnoreDeleted("p-1")).thenReturn(post);
            when(forumPostMapper.updateFlagStatusAt("p-1", true, "spam", flaggedAt)).thenReturn(1);

            var result = forumOwnerPort.flagPost("p-1", "spam", flaggedAt);

            assertThat(result.authorUserId()).isEqualTo("u-1");
            assertThat(result.previousIsFlagged()).isFalse();
            verify(forumPostMapper).updateFlagStatusAt("p-1", true, "spam", flaggedAt);
        }

        @Test
        @DisplayName("flagPost captures previous flag state for audit")
        void flagPost_capturesPreviousState() {
            ForumPost post = createPost("p-1", "u-1");
            post.setIsFlagged(true);
            post.setFlaggedReason("old-reason");
            LocalDateTime flaggedAt = LocalDateTime.now();
            when(forumPostMapper.selectByIdForUpdateIgnoreDeleted("p-1")).thenReturn(post);
            when(forumPostMapper.updateFlagStatusAt("p-1", true, "new-reason", flaggedAt)).thenReturn(1);

            var result = forumOwnerPort.flagPost("p-1", "new-reason", flaggedAt);

            assertThat(result.previousIsFlagged()).isTrue();
            assertThat(result.previousReason()).isEqualTo("old-reason");
        }
    }

    @Nested
    @DisplayName("unflagPost tests")
    class UnflagPostTests {

        @Test
        @DisplayName("unflagPost clears isFlagged and reason")
        void unflagPost_clearsFlag() {
            ForumPost post = createPost("p-1", "u-1");
            post.setIsFlagged(true);
            post.setFlaggedReason("spam");
            when(forumPostMapper.selectByIdForUpdateIgnoreDeleted("p-1")).thenReturn(post);
            when(forumPostMapper.updateFlagStatusAt("p-1", false, null, null)).thenReturn(1);

            var result = forumOwnerPort.unflagPost("p-1");

            assertThat(result.authorUserId()).isEqualTo("u-1");
            verify(forumPostMapper).updateFlagStatusAt("p-1", false, null, null);
        }
    }

    @Nested
    @DisplayName("toggle tests")
    class ToggleTests {

        @Test
        @DisplayName("setPinned throws NOT_FOUND when post missing")
        void setPinned_throwsWhenMissing() {
            when(forumPostMapper.selectByIdForUpdateIgnoreDeleted("missing")).thenReturn(null);

            assertThatThrownBy(() -> forumOwnerPort.setPinned("missing", true))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(BaseErrorCode.NOT_FOUND);
        }

        @Test
        @DisplayName("setPinned rejects a logically deleted post")
        void setPinned_rejectsDeletedPost() {
            ForumPost post = createPost("p-1", "u-1");
            post.setIsDeleted(true);
            when(forumPostMapper.selectByIdForUpdateIgnoreDeleted("p-1")).thenReturn(post);

            assertThatThrownBy(() -> forumOwnerPort.setPinned("p-1", true))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(BaseErrorCode.NOT_FOUND);
        }

        @Test
        @DisplayName("setLocked captures previous value for audit")
        void setLocked_capturesPrevious() {
            ForumPost post = createPost("p-1", "u-1");
            post.setIsLocked(false);
            when(forumPostMapper.selectByIdForUpdateIgnoreDeleted("p-1")).thenReturn(post);
            when(forumPostMapper.updateLockStatus("p-1", true)).thenReturn(1);

            var result = forumOwnerPort.setLocked("p-1", true);

            assertThat(result.previousState()).isFalse();
            verify(forumPostMapper).updateLockStatus("p-1", true);
        }
    }

    @Nested
    @DisplayName("deletePost tests")
    class DeletePostTests {

        @Test
        @DisplayName("deletePost rejects a missing post")
        void deletePost_rejectsMissingPost() {
            when(forumPostMapper.selectByIdForUpdateIgnoreDeleted("missing")).thenReturn(null);

            assertThatThrownBy(() -> forumOwnerPort.deletePost("missing", "admin-1"))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(BaseErrorCode.NOT_FOUND);
        }

        @Test
        @DisplayName("deletePost rejects an already deleted post")
        void deletePost_rejectsAlreadyDeletedPost() {
            ForumPost post = createPost("p-1", "u-1");
            post.setIsDeleted(true);
            when(forumPostMapper.selectByIdForUpdateIgnoreDeleted("p-1")).thenReturn(post);

            assertThatThrownBy(() -> forumOwnerPort.deletePost("p-1", "admin-1"))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(BaseErrorCode.NOT_FOUND);
        }

        @Test
        @DisplayName("deletePost rejects a soft-delete race")
        void deletePost_rejectsSoftDeleteRace() {
            ForumPost post = createPost("p-1", "u-1");
            when(forumPostMapper.selectByIdForUpdateIgnoreDeleted("p-1"))
                    .thenReturn(post)
                    .thenReturn(deletedPost("p-1", "u-1"));
            when(forumPostMapper.softDelete("p-1", "admin-1")).thenReturn(0);

            assertThatThrownBy(() -> forumOwnerPort.deletePost("p-1", "admin-1"))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(BaseErrorCode.NOT_FOUND);
        }

        @Test
        @DisplayName("deletePost returns the owner snapshot after a successful delete")
        void deletePost_returnsSnapshotAfterSuccess() {
            ForumPost post = createPost("p-1", "u-1");
            post.setTitle("Title");
            when(forumPostMapper.selectByIdForUpdateIgnoreDeleted("p-1")).thenReturn(post);
            when(forumPostMapper.softDelete("p-1", "admin-1")).thenReturn(1);

            var result = forumOwnerPort.deletePost("p-1", "admin-1");

            assertThat(result.authorUserId()).isEqualTo("u-1");
            assertThat(result.title()).isEqualTo("Title");
            verify(forumPostMapper).softDelete("p-1", "admin-1");
            verify(searchPublisher).publishForumPost(post, false);
        }

        private ForumPost deletedPost(String id, String userId) {
            ForumPost post = createPost(id, userId);
            post.setIsDeleted(true);
            return post;
        }
    }
}
