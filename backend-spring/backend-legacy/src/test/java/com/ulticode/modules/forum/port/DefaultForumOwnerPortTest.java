package com.ulticode.modules.forum.port;

import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
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

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
        post.setIsFlagged(false);
        post.setIsPinned(false);
        post.setIsLocked(false);
        return post;
    }

    @Nested
    @DisplayName("flagPost tests")
    class FlagPostTests {

        @Test
        @DisplayName("flagPost sets isFlagged=true and stamps reason and time, returning FlagResult")
        void flagPostSuccess() {
            ForumPost post = createPost("p1", "u1");
            when(forumPostMapper.selectById("p1")).thenReturn(post);

            LocalDateTime now = LocalDateTime.now();
            ForumOwnerPort.FlagResult result = forumOwnerPort.flagPost("p1", "Spam content", now);

            assertThat(result.authorUserId()).isEqualTo("u1");
            assertThat(result.previousIsFlagged()).isFalse();
            assertThat(result.previousFlaggedReason()).isEmpty();

            ArgumentCaptor<ForumPost> captor = ArgumentCaptor.forClass(ForumPost.class);
            verify(forumPostMapper).updateById(captor.capture());

            ForumPost updated = captor.getValue();
            assertThat(updated.getIsFlagged()).isTrue();
            assertThat(updated.getFlaggedReason()).isEqualTo("Spam content");
            assertThat(updated.getFlaggedAt()).isEqualTo(now);
        }

        @Test
        @DisplayName("flagPost throws NOT_FOUND when post does not exist")
        void flagPostNotFound() {
            when(forumPostMapper.selectById("missing")).thenReturn(null);

            assertThatThrownBy(() -> forumOwnerPort.flagPost("missing", "reason", LocalDateTime.now()))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(ErrorCode.NOT_FOUND.getCode());
        }
    }

    @Nested
    @DisplayName("unflagPost tests")
    class UnflagPostTests {

        @Test
        @DisplayName("unflagPost resets flag fields and returns previous values in FlagResult")
        void unflagPostSuccess() {
            ForumPost post = createPost("p1", "u1");
            post.setIsFlagged(true);
            post.setFlaggedReason("old reason");
            post.setFlaggedAt(LocalDateTime.now());
            when(forumPostMapper.selectById("p1")).thenReturn(post);

            ForumOwnerPort.FlagResult result = forumOwnerPort.unflagPost("p1");

            assertThat(result.authorUserId()).isEqualTo("u1");
            assertThat(result.previousIsFlagged()).isTrue();
            assertThat(result.previousFlaggedReason()).isEqualTo("old reason");

            ArgumentCaptor<ForumPost> captor = ArgumentCaptor.forClass(ForumPost.class);
            verify(forumPostMapper).updateById(captor.capture());

            ForumPost updated = captor.getValue();
            assertThat(updated.getIsFlagged()).isFalse();
            assertThat(updated.getFlaggedReason()).isNull();
            assertThat(updated.getFlaggedAt()).isNull();
        }
    }

    @Nested
    @DisplayName("toggle tests")
    class ToggleTests {

        @Test
        @DisplayName("setPinned updates isPinned and returns previous value")
        void setPinnedSuccess() {
            ForumPost post = createPost("p1", "u1");
            post.setIsPinned(false);
            when(forumPostMapper.selectById("p1")).thenReturn(post);

            ForumOwnerPort.ToggleResult result = forumOwnerPort.setPinned("p1", true);

            assertThat(result.authorUserId()).isEqualTo("u1");
            assertThat(result.previousValue()).isFalse();
            verify(forumPostMapper).updateById(post);
            assertThat(post.getIsPinned()).isTrue();
        }

        @Test
        @DisplayName("setLocked updates isLocked and returns previous value")
        void setLockedSuccess() {
            ForumPost post = createPost("p1", "u1");
            post.setIsLocked(true);
            when(forumPostMapper.selectById("p1")).thenReturn(post);

            ForumOwnerPort.ToggleResult result = forumOwnerPort.setLocked("p1", false);

            assertThat(result.authorUserId()).isEqualTo("u1");
            assertThat(result.previousValue()).isTrue();
            verify(forumPostMapper).updateById(post);
            assertThat(post.getIsLocked()).isFalse();
        }
    }
}
