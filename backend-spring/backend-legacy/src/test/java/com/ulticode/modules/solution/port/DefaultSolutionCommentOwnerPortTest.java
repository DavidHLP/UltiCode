package com.ulticode.modules.solution.port;

import com.ulticode.modules.solution.entity.SolutionComment;
import com.ulticode.modules.solution.mapper.SolutionCommentMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Focused tests for {@link DefaultSolutionCommentOwnerPort} verifying
 * behavioral parity with the original mapper-direct path:
 * flag/unflag delegate to atomic {@code updateFlagStatus} SQL with
 * pre-mutation snapshot via {@code selectByIdIgnoreDeleted};
 * resolveAuthorId/resolveSolutionId use {@code selectById};
 * missing rows return null without mutation.
 */
@ExtendWith(MockitoExtension.class)
class DefaultSolutionCommentOwnerPortTest {

    @Mock
    private SolutionCommentMapper solutionCommentMapper;

    private DefaultSolutionCommentOwnerPort port;

    @BeforeEach
    void setUp() {
        port = new DefaultSolutionCommentOwnerPort(solutionCommentMapper);
    }

    private SolutionComment comment(String id, String userId, String solutionId, boolean flagged) {
        SolutionComment c = new SolutionComment();
        c.setId(id);
        c.setUserId(userId);
        c.setSolutionId(solutionId);
        c.setIsFlagged(flagged);
        c.setFlaggedReason(flagged ? "old-reason" : null);
        return c;
    }

    @Nested
    @DisplayName("flagComment")
    class FlagComment {

        @Test
        @DisplayName("delegates to atomic updateFlagStatus and returns pre-state")
        void flagsAndReturnsPreState() {
            SolutionComment c = comment("c1", "user-a", "sol-1", false);
            when(solutionCommentMapper.selectByIdIgnoreDeleted("c1")).thenReturn(c);

            var result = port.flagComment("c1", "spam");

            assertThat(result).isNotNull();
            assertThat(result.authorUserId()).isEqualTo("user-a");
            assertThat(result.previousIsFlagged()).isFalse();
            assertThat(result.previousFlaggedReason()).isEqualTo("");
            verify(solutionCommentMapper).updateFlagStatus("c1", true, "spam");
        }

        @Test
        @DisplayName("returns null for missing comment without mutation")
        void missingReturnsNull() {
            when(solutionCommentMapper.selectByIdIgnoreDeleted("c1")).thenReturn(null);

            var result = port.flagComment("c1", "spam");

            assertThat(result).isNull();
            verify(solutionCommentMapper, never()).updateFlagStatus(anyString(), anyBoolean(), any());
        }
    }

    @Nested
    @DisplayName("unflagComment")
    class UnflagComment {

        @Test
        @DisplayName("delegates to atomic updateFlagStatus(false, null) and returns pre-state")
        void unflagsAndReturnsPreState() {
            SolutionComment c = comment("c1", "user-a", "sol-1", true);
            c.setFlaggedReason("old");
            when(solutionCommentMapper.selectByIdIgnoreDeleted("c1")).thenReturn(c);

            var result = port.unflagComment("c1");

            assertThat(result).isNotNull();
            assertThat(result.authorUserId()).isEqualTo("user-a");
            assertThat(result.previousIsFlagged()).isTrue();
            assertThat(result.previousFlaggedReason()).isEqualTo("old");
            verify(solutionCommentMapper).updateFlagStatus("c1", false, null);
        }

        @Test
        @DisplayName("returns null for missing comment without mutation")
        void missingReturnsNull() {
            when(solutionCommentMapper.selectByIdIgnoreDeleted("c1")).thenReturn(null);

            assertThat(port.unflagComment("c1")).isNull();
            verify(solutionCommentMapper, never()).updateFlagStatus(anyString(), anyBoolean(), any());
        }
    }

    @Nested
    @DisplayName("resolveAuthorId")
    class ResolveAuthorId {

        @Test
        @DisplayName("returns userId via selectById")
        void resolvesAuthor() {
            when(solutionCommentMapper.selectById("c1"))
                    .thenReturn(comment("c1", "user-a", "sol-1", false));

            assertThat(port.resolveAuthorId("c1")).isEqualTo("user-a");
        }

        @Test
        @DisplayName("returns null for missing comment")
        void missingReturnsNull() {
            when(solutionCommentMapper.selectById("c1")).thenReturn(null);
            assertThat(port.resolveAuthorId("c1")).isNull();
        }
    }

    @Nested
    @DisplayName("resolveSolutionId")
    class ResolveSolutionId {

        @Test
        @DisplayName("returns solutionId via selectById")
        void resolvesParent() {
            when(solutionCommentMapper.selectById("c1"))
                    .thenReturn(comment("c1", "user-a", "sol-42", false));

            assertThat(port.resolveSolutionId("c1")).isEqualTo("sol-42");
        }

        @Test
        @DisplayName("returns null for missing comment")
        void missingReturnsNull() {
            when(solutionCommentMapper.selectById("c1")).thenReturn(null);
            assertThat(port.resolveSolutionId("c1")).isNull();
        }
    }
}
