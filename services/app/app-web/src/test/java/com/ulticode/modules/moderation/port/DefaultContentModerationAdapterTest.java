package com.ulticode.modules.moderation.port;

import com.ulticode.app.api.dto.ContentLifecycleState;
import com.ulticode.modules.forum.port.ForumCommentOwnerPort;
import com.ulticode.modules.forum.port.ForumOwnerPort;
import com.ulticode.app.api.service.ProblemOwnerPort;
import com.ulticode.app.api.service.SolutionCommentOwnerPort;
import com.ulticode.app.api.service.SolutionOwnerPort;
import com.ulticode.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class DefaultContentModerationAdapterTest {

    private ForumOwnerPort forumOwnerPort;
    private ForumCommentOwnerPort forumCommentOwnerPort;
    private SolutionOwnerPort solutionOwnerPort;
    private SolutionCommentOwnerPort solutionCommentOwnerPort;
    private ProblemOwnerPort problemOwnerPort;

    private DefaultContentModerationAdapter adapter;

    @BeforeEach
    void setUp() {
        forumOwnerPort = mock(ForumOwnerPort.class);
        forumCommentOwnerPort = mock(ForumCommentOwnerPort.class);
        solutionOwnerPort = mock(SolutionOwnerPort.class);
        solutionCommentOwnerPort = mock(SolutionCommentOwnerPort.class);
        problemOwnerPort = mock(ProblemOwnerPort.class);

        adapter = new DefaultContentModerationAdapter(
                forumOwnerPort,
                forumCommentOwnerPort,
                solutionOwnerPort,
                solutionCommentOwnerPort,
                problemOwnerPort);
    }

    @Test
    @DisplayName("deleteContent delegates forum_post deletion to ForumOwnerPort")
    void deleteContentForumPost() {
        ContentLifecycleState state = adapter.deleteContent("forum_post", "post-100");

        assertThat(state).isEqualTo(ContentLifecycleState.DELETED);
        verify(forumOwnerPort).deletePost("post-100");
    }

    @Test
    @DisplayName("deleteContent delegates solution deletion to SolutionOwnerPort")
    void deleteContentSolution() {
        ContentLifecycleState state = adapter.deleteContent("solution", "sol-100");

        assertThat(state).isEqualTo(ContentLifecycleState.DELETED);
        verify(solutionOwnerPort).deleteSolution("sol-100");
    }

    @Test
    @DisplayName("deleteContent throws BusinessException for unknown contentType")
    void deleteContentUnknownType() {
        assertThatThrownBy(() -> adapter.deleteContent("unknown_type", "id-100"))
                .isInstanceOf(BusinessException.class);
    }
}
