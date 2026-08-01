package com.ulticode.modules.forum.service.impl;

import com.ulticode.modules.forum.dto.ForumCommentVO;
import com.ulticode.modules.forum.dto.UpdateCommentDTO;
import com.ulticode.modules.forum.entity.ForumComment;
import com.ulticode.modules.forum.lifecycle.ForumUserLifecyclePort;
import com.ulticode.modules.forum.mapper.ForumCommentMapper;
import com.ulticode.modules.forum.mapper.ForumPostMapper;
import com.ulticode.modules.forum.port.ForumUserReadPort;
import com.ulticode.modules.forum.projection.ForumCommentProjection;
import com.ulticode.modules.forum.service.ForumCommentService;
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
import java.time.ZoneId;

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
 *
 * <p>P7-RELOCATE-FORUM-001: {@code UserReadProjection} replaced with
 * {@link ForumUserReadPort}.
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
    private ForumUserLifecyclePort forumUserLifecycle;
    @Mock
    private ForumUserReadPort forumUserReadPort;
    @Mock
    private ForumCommentProjection commentProjection;

    private ForumCommentServiceImpl service;

    @BeforeEach
    void setUp() {
        // Shared clock fixed to 2026-06-01 for consistent editedAt assertions
        Clock fixedClock = Clock.fixed(
                LocalDateTime.of(2026, 6, 1, 12, 0)
                        .toInstant(ZoneId.systemDefault().getRules()
                                .getOffset(java.time.Instant.now())),
                ZoneId.systemDefault());
        service = new ForumCommentServiceImpl(
                commentMapper, postMapper, forumUserLifecycle,
                forumUserReadPort, commentProjection, fixedClock);
    }

    @Test
    @DisplayName("updateComment returns VO with non-null editedAt after PATCH (regression)")
    void updateComment_setsEditedAtOnReturnedVO() {
        ForumComment existing = new ForumComment();
        existing.setId(COMMENT_ID);
        existing.setAuthorId(AUTHOR_ID);
        existing.setPostId(POST_ID);
        existing.setBody("original");
        existing.setMarkdown("original");
        existing.setCreatedAt(LocalDateTime.of(2026, 5, 1, 0, 0));
        existing.setEditedAt(null);

        ForumCommentVO expectedVO = new ForumCommentVO();
        expectedVO.setId(COMMENT_ID);
        expectedVO.setEditedAt(LocalDateTime.of(2026, 6, 1, 12, 0));

        when(commentMapper.selectById(COMMENT_ID)).thenReturn(existing);
        when(commentMapper.updateById(any(ForumComment.class))).thenReturn(1);
        when(commentMapper.markAsEdited(COMMENT_ID)).thenReturn(1);
        ForumUserReadPort.UserSummary author =
                new ForumUserReadPort.UserSummary(AUTHOR_ID, "alice", "avatar");
        when(forumUserReadPort.findById(AUTHOR_ID)).thenReturn(author);
        when(commentProjection.toCommentVO(any(ForumComment.class), any()))
                .thenReturn(expectedVO);

        UpdateCommentDTO dto = new UpdateCommentDTO();
        dto.setBody("updated");
        ForumCommentVO result = service.updateComment(COMMENT_ID, dto, AUTHOR_ID);

        assertThat(result.getEditedAt()).isNotNull();
    }
}
