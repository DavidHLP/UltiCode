package com.ulticode.modules.moderation.port;

import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.error.BaseErrorCode;
import com.ulticode.app.error.SolutionErrorCode;
import com.ulticode.app.api.service.ForumCommentOwnerPort;
import com.ulticode.app.api.service.ForumOwnerPort;
import com.ulticode.app.api.service.ProblemOwnerPort;
import com.ulticode.app.api.service.SolutionCommentOwnerPort;
import com.ulticode.app.api.service.SolutionOwnerPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Focused tests for {@link DefaultContentModerationAdapter} verifying that
 * the owner-port dispatch correctly routes author resolution and flag/unflag
 * operations for all five entity types, plus null/unknown no-op behavior.
 *
 * <p>P7-MODERATION-CUTOVER-001: proves the adapter delegates entirely to
 * owner ports with zero mapper dependencies.
 */
@ExtendWith(MockitoExtension.class)
class DefaultContentModerationAdapterTest {

    @Mock private ForumOwnerPort forumOwnerPort;
    @Mock private ForumCommentOwnerPort forumCommentOwnerPort;
    @Mock private SolutionOwnerPort solutionOwnerPort;
    @Mock private SolutionCommentOwnerPort solutionCommentOwnerPort;
    @Mock private ProblemOwnerPort problemOwnerPort;

    private DefaultContentModerationAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new DefaultContentModerationAdapter(
                forumOwnerPort, forumCommentOwnerPort,
                solutionOwnerPort, solutionCommentOwnerPort,
                problemOwnerPort);
    }

    @Nested
    @DisplayName("resolveAuthorId dispatch")
    class ResolveAuthorId {

        @Test
        @DisplayName("forum_post delegates to ForumOwnerPort")
        void forumPost() {
            when(forumOwnerPort.resolveAuthorId("p1")).thenReturn("user-a");
            assertThat(adapter.resolveAuthorId("forum_post", "p1")).isEqualTo("user-a");
        }

        @Test
        @DisplayName("forum_comment delegates to ForumCommentOwnerPort")
        void forumComment() {
            when(forumCommentOwnerPort.resolveAuthorId("c1")).thenReturn("user-b");
            assertThat(adapter.resolveAuthorId("forum_comment", "c1")).isEqualTo("user-b");
        }

        @Test
        @DisplayName("solution delegates to SolutionOwnerPort")
        void solution() {
            when(solutionOwnerPort.resolveAuthorId("s1")).thenReturn("user-c");
            assertThat(adapter.resolveAuthorId("solution", "s1")).isEqualTo("user-c");
        }

        @Test
        @DisplayName("solution_comment delegates to SolutionCommentOwnerPort")
        void solutionComment() {
            when(solutionCommentOwnerPort.resolveAuthorId("sc1")).thenReturn("user-d");
            assertThat(adapter.resolveAuthorId("solution_comment", "sc1")).isEqualTo("user-d");
        }

        @Test
        @DisplayName("problem delegates to ProblemOwnerPort")
        void problem() {
            when(problemOwnerPort.resolveAuthorId("42")).thenReturn("user-e");
            assertThat(adapter.resolveAuthorId("problem", "42")).isEqualTo("user-e");
        }

        @Test
        @DisplayName("null entityType returns null without delegation")
        void nullEntityType() {
            assertThat(adapter.resolveAuthorId(null, "x")).isNull();
            verifyNoInteractions(forumOwnerPort);
        }

        @Test
        @DisplayName("null entityId returns null without delegation")
        void nullEntityId() {
            assertThat(adapter.resolveAuthorId("forum_post", null)).isNull();
            verifyNoInteractions(forumOwnerPort);
        }

        @Test
        @DisplayName("unknown entity type returns null")
        void unknownType() {
            assertThat(adapter.resolveAuthorId("unknown_type", "x")).isNull();
            verifyNoInteractions(forumOwnerPort, solutionOwnerPort, problemOwnerPort);
        }
    }

    @Nested
    @DisplayName("updateFlagStatus dispatch")
    class UpdateFlagStatus {

        @Test
        @DisplayName("forum_post flag calls flagPost")
        void flagForumPost() {
            adapter.updateFlagStatus("forum_post", "p1", true, "spam");
            verify(forumOwnerPort).flagPost(eq("p1"), eq("spam"), any(LocalDateTime.class));
            verify(forumOwnerPort, never()).unflagPost(anyString());
        }

        @Test
        @DisplayName("forum_post unflag calls unflagPost")
        void unflagForumPost() {
            adapter.updateFlagStatus("forum_post", "p1", false, null);
            verify(forumOwnerPort).unflagPost("p1");
            verify(forumOwnerPort, never()).flagPost(anyString(), anyString(), any());
        }

        @Test
        @DisplayName("forum_comment flag calls flagComment")
        void flagForumComment() {
            adapter.updateFlagStatus("forum_comment", "c1", true, "abuse");
            verify(forumCommentOwnerPort).flagComment("c1", "abuse");
        }

        @Test
        @DisplayName("forum_comment unflag calls unflagComment")
        void unflagForumComment() {
            adapter.updateFlagStatus("forum_comment", "c1", false, null);
            verify(forumCommentOwnerPort).unflagComment("c1");
        }

        @Test
        @DisplayName("solution flag calls flagSolution")
        void flagSolution() {
            adapter.updateFlagStatus("solution", "s1", true, "off-topic");
            verify(solutionOwnerPort).flagSolution(eq("s1"), eq("off-topic"), any(LocalDateTime.class));
        }

        @Test
        @DisplayName("solution unflag calls unflagSolution")
        void unflagSolution() {
            adapter.updateFlagStatus("solution", "s1", false, null);
            verify(solutionOwnerPort).unflagSolution("s1");
        }

        @Test
        @DisplayName("solution_comment flag calls flagComment")
        void flagSolutionComment() {
            adapter.updateFlagStatus("solution_comment", "sc1", true, "spam");
            verify(solutionCommentOwnerPort).flagComment("sc1", "spam");
        }

        @Test
        @DisplayName("solution_comment unflag calls unflagComment")
        void unflagSolutionComment() {
            adapter.updateFlagStatus("solution_comment", "sc1", false, null);
            verify(solutionCommentOwnerPort).unflagComment("sc1");
        }

        @Test
        @DisplayName("problem delegates to updateModerationFlag")
        void problem() {
            adapter.updateFlagStatus("problem", "42", true, "review");
            verify(problemOwnerPort).updateModerationFlag("42", true, "review");
        }

        @Test
        @DisplayName("null entityType is a no-op")
        void nullType() {
            adapter.updateFlagStatus(null, "x", true, "r");
            verifyNoInteractions(forumOwnerPort);
        }

        @Test
        @DisplayName("null entityId is a no-op")
        void nullId() {
            adapter.updateFlagStatus("forum_post", null, true, "r");
            verifyNoInteractions(forumOwnerPort);
        }

        @Test
        @DisplayName("unknown entity type is a no-op")
        void unknownType() {
            adapter.updateFlagStatus("unknown", "x", true, "r");
            verifyNoInteractions(forumOwnerPort, solutionOwnerPort, problemOwnerPort);
        }

        @Test
        @DisplayName("forum_post flag silently no-ops when post already deleted")
        void flagForumPost_whenPostDeleted_silentlyNoOps() {
            doThrow(new BusinessException(BaseErrorCode.NOT_FOUND))
                    .when(forumOwnerPort).flagPost(eq("p1"), eq("r"), any(LocalDateTime.class));

            assertThatCode(() -> adapter.updateFlagStatus("forum_post", "p1", true, "r"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("forum_post unflag silently no-ops when post already deleted")
        void unflagForumPost_whenPostDeleted_silentlyNoOps() {
            doThrow(new BusinessException(BaseErrorCode.NOT_FOUND))
                    .when(forumOwnerPort).unflagPost("p1");

            assertThatCode(() -> adapter.updateFlagStatus("forum_post", "p1", false, null))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("solution flag silently no-ops when solution already deleted")
        void flagSolution_whenSolutionDeleted_silentlyNoOps() {
            doThrow(new BusinessException(SolutionErrorCode.SOLUTION_NOT_FOUND))
                    .when(solutionOwnerPort).flagSolution(eq("s1"), eq("r"), any(LocalDateTime.class));

            assertThatCode(() -> adapter.updateFlagStatus("solution", "s1", true, "r"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("solution unflag silently no-ops when solution already deleted")
        void unflagSolution_whenSolutionDeleted_silentlyNoOps() {
            doThrow(new BusinessException(SolutionErrorCode.SOLUTION_NOT_FOUND))
                    .when(solutionOwnerPort).unflagSolution("s1");

            assertThatCode(() -> adapter.updateFlagStatus("solution", "s1", false, null))
                    .doesNotThrowAnyException();
        }

    }
}
