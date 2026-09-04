package com.ulticode.modules.forum.port;

import com.ulticode.modules.forum.port.ForumCommentOwnerPort;
import com.ulticode.modules.forum.entity.ForumComment;
import com.ulticode.modules.forum.mapper.ForumCommentMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultForumCommentOwnerPortTest {

    @Mock
    private ForumCommentMapper forumCommentMapper;

    private DefaultForumCommentOwnerPort ownerPort;

    @BeforeEach
    void setUp() {
        ownerPort = new DefaultForumCommentOwnerPort(forumCommentMapper);
    }

    @Test
    void flagReturnsPreviousStateAfterActiveRowUpdate() {
        ForumComment comment = comment(false, "old reason");
        when(forumCommentMapper.selectByIdIgnoreDeleted("comment-1")).thenReturn(comment);
        when(forumCommentMapper.updateFlagStatus("comment-1", true, "spam")).thenReturn(1);

        ForumCommentOwnerPort.FlagResult result = ownerPort.flagComment("comment-1", "spam");

        assertThat(result.authorId()).isEqualTo("author-1");
        assertThat(result.previousWasFlagged()).isFalse();
        assertThat(result.previousReason()).isEqualTo("old reason");
        verify(forumCommentMapper).updateFlagStatus("comment-1", true, "spam");
    }

    @Test
    void unflagReturnsNullWhenRowWasDeletedBeforeMutation() {
        ForumComment comment = comment(true, "spam");
        when(forumCommentMapper.selectByIdIgnoreDeleted("comment-1")).thenReturn(comment);
        when(forumCommentMapper.updateFlagStatus("comment-1", false, null)).thenReturn(0);

        assertThat(ownerPort.unflagComment("comment-1")).isNull();
        verify(forumCommentMapper).updateFlagStatus("comment-1", false, null);
    }

    @Test
    void deleteReturnsPreviousStateAfterActiveRowUpdate() {
        ForumComment comment = comment(false, null);
        when(forumCommentMapper.selectByIdIgnoreDeleted("comment-1")).thenReturn(comment);
        when(forumCommentMapper.softDelete("comment-1", "admin-1")).thenReturn(1);

        ForumCommentOwnerPort.DeleteResult result = ownerPort.deleteComment("comment-1", "admin-1");

        assertThat(result.authorUserId()).isEqualTo("author-1");
        assertThat(result.previousIsDeleted()).isFalse();
        verify(forumCommentMapper).softDelete("comment-1", "admin-1");
    }

    @Test
    void deleteReturnsNullWhenRowWasDeletedBeforeMutation() {
        ForumComment comment = comment(false, null);
        when(forumCommentMapper.selectByIdIgnoreDeleted("comment-1")).thenReturn(comment);
        when(forumCommentMapper.softDelete("comment-1", "admin-1")).thenReturn(0);

        assertThat(ownerPort.deleteComment("comment-1", "admin-1")).isNull();
        verify(forumCommentMapper).softDelete("comment-1", "admin-1");
    }

    private static ForumComment comment(boolean flagged, String reason) {
        ForumComment comment = new ForumComment();
        comment.setId("comment-1");
        comment.setAuthorId("author-1");
        comment.setIsFlagged(flagged);
        comment.setFlaggedReason(reason);
        comment.setIsDeleted(false);
        return comment;
    }
}
