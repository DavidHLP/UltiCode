package com.ulticode.modules.forum.service.impl;

import com.ulticode.modules.forum.dto.ForumCommentVO;
import com.ulticode.modules.forum.dto.UpdateCommentDTO;
import com.ulticode.modules.forum.entity.ForumComment;
import com.ulticode.modules.forum.mapper.ForumCommentMapper;
import com.ulticode.modules.forum.mapper.ForumPostMapper;
import com.ulticode.modules.forum.mapper.ForumUserMapper;
import com.ulticode.modules.user.projection.UserReadProjection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ForumCommentServiceImpl}.
 *
 * <p>Primary regression test: {@code updateComment} must return a VO whose
 * {@code editedAt} is non-null after a PATCH. Pre-fix behavior was that
 * the mapper's {@code markAsEdited(id)} wrote {@code edited_at = NOW()} to
 * the DB but the in-memory {@link ForumComment} object was never refreshed,
 * so {@code convertToCommentVO} returned {@code editedAt=null}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ForumCommentServiceImplTest {

    private static final String COMMENT_ID = "c-001";
    private static final String AUTHOR_ID = "u-001";
    private static final String POST_ID = "p-001";

    @Mock
    private ForumCommentMapper commentMapper;
    @Mock
    private ForumPostMapper postMapper;
    @Mock
    private ForumUserMapper forumUserMapper;
    @Mock
    private UserReadProjection userReadProjection;
    @Mock
    private Clock clock;

    @InjectMocks
    private ForumCommentServiceImpl service;

    @BeforeEach
    void setUp() {
        // Stub the Clock so LocalDateTime.now(clock) doesn't NPE on a fresh
        // mock — the service reads editedAt via LocalDateTime.now(clock).
        when(clock.instant()).thenReturn(java.time.Instant.now());
        when(clock.getZone()).thenReturn(java.time.ZoneId.systemDefault());
    }

    @Test
    @DisplayName("updateComment returns VO with non-null editedAt after PATCH (regression)")
    void updateComment_setsEditedAtOnReturnedVO() {
        ForumComment existing = new ForumComment();
        existing.setId(COMMENT_ID);
        existing.setPostId(POST_ID);
        existing.setAuthorId(AUTHOR_ID);
        existing.setBody("old body");
        existing.setCreatedAt(LocalDateTime.now().minusDays(1));
        existing.setEditedAt(null);

        when(commentMapper.selectById(COMMENT_ID)).thenReturn(existing);
        when(commentMapper.updateById(any(ForumComment.class))).thenReturn(1);
        when(commentMapper.markAsEdited(COMMENT_ID)).thenReturn(1);
        when(userReadProjection.findById(AUTHOR_ID)).thenReturn(Optional.empty());

        UpdateCommentDTO dto = new UpdateCommentDTO();
        dto.setBody("new body");

        ForumCommentVO vo = service.updateComment(COMMENT_ID, dto, AUTHOR_ID);

        assertThat(vo).isNotNull();
        assertThat(vo.getBody()).isEqualTo("new body");
        // The fix: editedAt must be set on the in-memory object so the VO returns it
        assertThat(vo.getEditedAt())
                .as("editedAt must reflect the actual edit time, not stale null")
                .isNotNull();
        assertThat(vo.getEditedAt()).isAfter(existing.getCreatedAt());
    }
}
