package com.ulticode.modules.admin.service.impl;

import com.ulticode.common.auth.CurrentUserProvider;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.modules.admin.port.AdminCommentReadPort;
import com.ulticode.modules.admin.service.comment.CommentModerator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AdminCommentServiceImpl} after the
 * {@link CommentModerator} seam extraction.
 *
 * <p>The previous test directly stubbed {@code ForumCommentMapper} and
 * {@code SolutionCommentMapper}; after the refactor the service has no
 * direct dependency on either mapper — it owns a {@code List<CommentModerator>}
 * and delegates. So this test now mocks two {@code CommentModerator}s
 * (one per type tag) and verifies the router behavior:
 * <ul>
 *   <li>type-keyed dispatch lands on the right moderator</li>
 *   <li>unknown type throws before any moderator is invoked</li>
 *   <li>unknown-id deletion bubbles the moderator's BusinessException</li>
 * </ul>
 *
 * <p>The deeper mutation-path tests (real LambdaUpdateWrapper + MyBatis-Plus
 * lambda cache) still need a Spring context and remain covered by the
 * {@code docs/comments-api-test-report.md} curl integration suite.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminCommentServiceImplTest {

    @Mock private CommentModerator forumModerator;
    @Mock private CommentModerator solutionModerator;
    @Mock private AdminCommentReadPort commentReadPort;
    @Mock private CurrentUserProvider currentUserProvider;

    private AdminCommentServiceImpl service;

    private static final String FORUM_COMMENT_ID = "fcmt-001-002";
    private static final String SOLUTION_COMMENT_ID = "scmt-001-002";

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin-id", null, Collections.emptyList()));
        when(forumModerator.getType()).thenReturn("forum");
        when(solutionModerator.getType()).thenReturn("solution");
        service = new AdminCommentServiceImpl(
                List.of(forumModerator, solutionModerator),
                commentReadPort,
                currentUserProvider,
                new com.ulticode.modules.admin.bulk.AdminBulkExecutor());
        service.indexModeratorsByType();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Service bean is constructible with moderator list + read port")
    void service_constructs() {
        assertNotNull(service, "Service should be constructible via @InjectMocks-style wiring");
    }

    @Test
    @DisplayName("deleteComment with unknown id delegates to forum moderator, which throws BusinessException")
    void deleteComment_unknownId_throwsBusinessException() {
        org.mockito.Mockito.doThrow(new BusinessException(ErrorCode.NOT_FOUND))
                .when(forumModerator).deleteComment("missing");

        assertThrows(
                BusinessException.class,
                () -> service.deleteComment("missing", "forum"));

        verify(forumModerator).deleteComment("missing");
        verify(solutionModerator, never()).deleteComment(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("unflagComment with invalid type throws BusinessException before touching any moderator")
    void unflagComment_invalidType_throwsBeforeMutation() {
        assertThrows(
                BusinessException.class,
                () -> service.unflagComment(FORUM_COMMENT_ID, "post"));

        verify(forumModerator, never()).unflagComment(org.mockito.ArgumentMatchers.any());
        verify(solutionModerator, never()).unflagComment(org.mockito.ArgumentMatchers.any());
    }
}
