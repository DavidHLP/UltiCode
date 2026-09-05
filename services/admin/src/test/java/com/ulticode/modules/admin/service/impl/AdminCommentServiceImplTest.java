package com.ulticode.modules.admin.service.impl;
import com.ulticode.admin.error.AdminErrorCode;
import com.ulticode.common.auth.CurrentUserProvider;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.response.DegradationStatus;
import com.ulticode.common.response.PageResult;
import com.ulticode.modules.admin.dto.AdminCommentQueryDTO;
import com.ulticode.modules.admin.dto.AdminCommentVO;
import com.ulticode.modules.admin.port.AdminCommentReadPort;
import com.ulticode.modules.admin.service.comment.CommentModerator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AdminCommentServiceImpl} after the
 * {@link CommentModerator} seam extraction.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("DefaultAdminCommentServiceImpl")
class AdminCommentServiceImplTest {

    @Mock private CommentModerator forumModerator;
    @Mock private CommentModerator solutionModerator;
    @Mock private AdminCommentReadPort commentReadPort;
    @Mock private CurrentUserProvider currentUserProvider;

    private AdminCommentServiceImpl service;

    private static final String FORUM_COMMENT_ID = "fcmt-001-002";
    private static final String FORUM_TYPE = "forum";
    private static final String SOLUTION_TYPE = "solution";

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin-id", null, Collections.emptyList()));
        when(forumModerator.getType()).thenReturn(FORUM_TYPE);
        when(solutionModerator.getType()).thenReturn(SOLUTION_TYPE);
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
        org.mockito.Mockito.doThrow(new BusinessException(AdminErrorCode.NOT_FOUND))
                .when(forumModerator).deleteComment("missing");

        assertThrows(
                BusinessException.class,
                () -> service.deleteComment("missing", FORUM_TYPE));

        verify(forumModerator).deleteComment("missing");
        verify(solutionModerator, never()).deleteComment(any());
    }

    @Test
    @DisplayName("unflagComment with invalid type throws BusinessException before touching any moderator")
    void unflagComment_invalidType_throwsBeforeMutation() {
        assertThrows(
                BusinessException.class,
                () -> service.unflagComment(FORUM_COMMENT_ID, "post"));

        verify(forumModerator, never()).unflagComment(any());
        verify(solutionModerator, never()).unflagComment(any());
    }

    @Test
    @DisplayName("getAllComments uses bounded page size 100, fetches exactly one page per moderator")
    void getAllComments_boundedPageSizeNotIntegerMax() {
        AdminCommentQueryDTO query = new AdminCommentQueryDTO();
        query.setType(null);
        query.setPage(1);
        query.setLimit(10);

        LocalDateTime now = LocalDateTime.now();

        // Each moderator returns exactly 100 items on page 1, but reports a
        // larger total (500 and 300 respectively) to verify total reflects
        // the bounded fetched count, NOT the owner's PageResult.getTotal().
        List<AdminCommentVO> forumItems = new ArrayList<>(100);
        for (int i = 0; i < 100; i++) {
            forumItems.add(new AdminCommentVO(
                    "fcmt-" + i, "content", now, now,
                    null, null, null, null, null, null,
                    false, null, null, false, null, null));
        }
        List<AdminCommentVO> solutionItems = new ArrayList<>(100);
        for (int i = 0; i < 100; i++) {
            solutionItems.add(new AdminCommentVO(
                    "scmt-" + i, "content", now, now,
                    null, null, null, null, null, null,
                    false, null, null, false, null, null));
        }

        // Page 1 returns 100 items, total is 500 for forum and 300 for solution.
        when(forumModerator.listComments(eq(query), eq(1), eq(100)))
                .thenReturn(PageResult.of(forumItems, 500L, 1, 100));
        when(solutionModerator.listComments(eq(query), eq(1), eq(100)))
                .thenReturn(PageResult.of(solutionItems, 300L, 1, 100));
        // Page 2 must NOT be fetched — bounded merge is 1 page per moderator.
        when(forumModerator.listComments(eq(query), eq(2), eq(100)))
                .thenThrow(new AssertionError("Page 2 should not be fetched for forum; bounded merge is 1 page per moderator"));
        when(solutionModerator.listComments(eq(query), eq(2), eq(100)))
                .thenThrow(new AssertionError("Page 2 should not be fetched for solution; bounded merge is 1 page per moderator"));

        PageResult<AdminCommentVO> result = service.getComments(query);

        // Verify each moderator was called exactly once with page 1 and page size 100.
        ArgumentCaptor<Integer> forumPageCaptor = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> forumSizeCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(forumModerator).listComments(eq(query), forumPageCaptor.capture(), forumSizeCaptor.capture());
        assertThat(forumPageCaptor.getValue()).isEqualTo(1);
        assertThat(forumSizeCaptor.getValue()).isEqualTo(100); // bounded, never Integer.MAX_VALUE

        ArgumentCaptor<Integer> solutionPageCaptor = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> solutionSizeCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(solutionModerator).listComments(eq(query), solutionPageCaptor.capture(), solutionSizeCaptor.capture());
        assertThat(solutionPageCaptor.getValue()).isEqualTo(1);
        assertThat(solutionSizeCaptor.getValue()).isEqualTo(100); // bounded, never Integer.MAX_VALUE
        // Total is the bounded fetched count (200 = 100+100 items), NOT
        // the owner's summed PageResult.getTotal() (500+300=800).
        // getComments paginates the merged result with limit=10 on page 1.
        assertThat(result.getTotal()).isEqualTo(200L);
        assertThat(result.getItems()).hasSize(10);
    }

    @Test
    @DisplayName("getAllComments returns PARTIAL when one moderator throws OWNER_QUERY_UNAVAILABLE")
    void getAllComments_oneModeratorUnavailable_returnsPartial() {
        AdminCommentQueryDTO query = new AdminCommentQueryDTO();
        query.setType(null);
        query.setPage(1);
        query.setLimit(10);

        List<AdminCommentVO> forumItems = new ArrayList<>(100);
        for (int i = 0; i < 100; i++) {
            forumItems.add(new AdminCommentVO(
                    "fcmt-" + i, "content", LocalDateTime.now(), LocalDateTime.now(),
                    null, null, null, null, null, null,
                    false, null, null, false, null, null));
        }

        when(forumModerator.listComments(eq(query), eq(1), eq(100)))
                .thenReturn(PageResult.of(forumItems, 100L, 1, 100));
        when(solutionModerator.listComments(eq(query), eq(1), eq(100)))
                .thenThrow(new BusinessException(AdminErrorCode.OWNER_QUERY_UNAVAILABLE,
                        "solution owner unavailable"));

        PageResult<AdminCommentVO> result = service.getComments(query);

        assertThat(result.getTotal()).isEqualTo(100L);
        assertThat(result.getItems()).hasSize(10);
        assertThat(result.getDegradationStatus()).isEqualTo(DegradationStatus.PARTIAL);
    }

    @Test
    @DisplayName("getAllComments throws OWNER_QUERY_UNAVAILABLE when all moderators fail")
    void getAllComments_allModeratorsUnavailable_throws() {
        AdminCommentQueryDTO query = new AdminCommentQueryDTO();
        query.setType(null);
        query.setPage(1);
        query.setLimit(10);

        when(forumModerator.listComments(eq(query), eq(1), eq(100)))
                .thenThrow(new BusinessException(AdminErrorCode.OWNER_QUERY_UNAVAILABLE,
                        "forum owner unavailable"));
        when(solutionModerator.listComments(eq(query), eq(1), eq(100)))
                .thenThrow(new BusinessException(AdminErrorCode.OWNER_QUERY_UNAVAILABLE,
                        "solution owner unavailable"));

        BusinessException thrown = assertThrows(
                BusinessException.class,
                () -> service.getComments(query));
        assertThat(thrown.getErrorCode()).isEqualTo(AdminErrorCode.OWNER_QUERY_UNAVAILABLE);
    }

    @Test
    @DisplayName("getAllComments propagates FORBIDDEN without degradation")
    void getAllComments_forbiddenExceptionPropagates() {
        AdminCommentQueryDTO query = new AdminCommentQueryDTO();
        query.setType(null);
        query.setPage(1);
        query.setLimit(10);

        when(solutionModerator.listComments(eq(query), eq(1), eq(100)))
                .thenThrow(new BusinessException(AdminErrorCode.FORBIDDEN,
                        "permission denied"));

        BusinessException thrown = assertThrows(
                BusinessException.class,
                () -> service.getComments(query));
        assertThat(thrown.getErrorCode()).isEqualTo(AdminErrorCode.FORBIDDEN);
    }
}
