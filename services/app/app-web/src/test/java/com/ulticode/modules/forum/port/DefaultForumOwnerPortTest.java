package com.ulticode.modules.forum.port;

import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.error.BaseErrorCode;
import com.ulticode.modules.forum.entity.ForumPost;
import com.ulticode.modules.forum.mapper.ForumPostMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.Serializable;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultForumOwnerPortTest {

    @Mock
    private ForumPostMapper forumPostMapper;

    private DefaultForumOwnerPort forumOwnerPort;

    @BeforeEach
    void setUp() {
        forumOwnerPort = new DefaultForumOwnerPort(forumPostMapper);
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
            when(forumPostMapper.selectById("p-1")).thenReturn(post);
            when(forumPostMapper.updateById(any(ForumPost.class))).thenReturn(1);

            var result = forumOwnerPort.flagPost("p-1", "spam", LocalDateTime.now());

            assertThat(result.authorUserId()).isEqualTo("u-1");
            assertThat(result.previousIsFlagged()).isFalse();
            ArgumentCaptor<ForumPost> captor = ArgumentCaptor.forClass(ForumPost.class);
            verify(forumPostMapper).updateById(captor.capture());
            assertThat(captor.getValue().getIsFlagged()).isTrue();
            assertThat(captor.getValue().getFlaggedReason()).isEqualTo("spam");
        }

        @Test
        @DisplayName("flagPost captures previous flag state for audit")
        void flagPost_capturesPreviousState() {
            ForumPost post = createPost("p-1", "u-1");
            post.setIsFlagged(true);
            post.setFlaggedReason("old-reason");
            when(forumPostMapper.selectById("p-1")).thenReturn(post);
            when(forumPostMapper.updateById(any(ForumPost.class))).thenReturn(1);

            var result = forumOwnerPort.flagPost("p-1", "new-reason", LocalDateTime.now());

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
            when(forumPostMapper.selectById("p-1")).thenReturn(post);
            when(forumPostMapper.updateById(any(ForumPost.class))).thenReturn(1);

            var result = forumOwnerPort.unflagPost("p-1");

            assertThat(result.authorUserId()).isEqualTo("u-1");
            ArgumentCaptor<ForumPost> captor = ArgumentCaptor.forClass(ForumPost.class);
            verify(forumPostMapper).updateById(captor.capture());
            assertThat(captor.getValue().getIsFlagged()).isFalse();
            assertThat(captor.getValue().getFlaggedReason()).isNull();
        }
    }

    @Nested
    @DisplayName("toggle tests")
    class ToggleTests {

        @Test
        @DisplayName("setPinned throws NOT_FOUND when post missing")
        void setPinned_throwsWhenMissing() {
            when(forumPostMapper.selectById("missing")).thenReturn(null);

            assertThatThrownBy(() -> forumOwnerPort.setPinned("missing", true))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(BaseErrorCode.NOT_FOUND);
        }

        @Test
        @DisplayName("setLocked captures previous value for audit")
        void setLocked_capturesPrevious() {
            ForumPost post = createPost("p-1", "u-1");
            post.setIsLocked(false);
            when(forumPostMapper.selectById("p-1")).thenReturn(post);
            when(forumPostMapper.updateById(any(ForumPost.class))).thenReturn(1);

            var result = forumOwnerPort.setLocked("p-1", true);

            assertThat(result.previousState()).isFalse();
        }
    }
}
