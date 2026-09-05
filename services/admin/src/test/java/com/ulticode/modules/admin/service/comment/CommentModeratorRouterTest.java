package com.ulticode.modules.admin.service.comment;

import com.ulticode.common.auth.CurrentUserProvider;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.response.PageResult;
import com.ulticode.modules.admin.dto.AdminCommentQueryDTO;
import com.ulticode.modules.admin.dto.AdminCommentVO;
import com.ulticode.modules.admin.port.AdminCommentReadPort;
import com.ulticode.modules.admin.service.impl.AdminCommentServiceImpl;
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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Router dispatch test for the {@link CommentModerator} seam.
 *
 * <p>Verifies that {@link AdminCommentServiceImpl} routes each of the five
 * moderated operations to the {@link CommentModerator} keyed by
 * {@link CommentModerator#getType()}, with no fallback to direct mapper
 * access. Also covers the two cross-moderator paths that legitimately
 * stay in the router:
 * <ol>
 *   <li>type=null on {@code getComments} iterates every moderator and
 *       merges results, paginated</li>
 *   <li>unknown type tags throw {@link BusinessException} before any
 *       moderator is invoked</li>
 * </ol>
 *
 * <p>Each {@link CommentModerator} is mocked — this test does NOT exercise
 * the real {@link ForumCommentModerator} / {@link SolutionCommentModerator}
 * bodies. The deep-module bodies are covered by integration tests
 * (see {@code docs/comments-api-test-report.md}).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CommentModeratorRouterTest {

    @Mock private CommentModerator forumModerator;
    @Mock private CommentModerator solutionModerator;
    @Mock private CommentModerator contestModerator;
    @Mock private AdminCommentReadPort commentReadPort;
    @Mock private CurrentUserProvider currentUserProvider;

    private AdminCommentServiceImpl router;

    private static final String FORUM_TYPE = "forum";
    private static final String SOLUTION_TYPE = "solution";
    private static final String CONTEST_TYPE = "contest";
    private static final String FORUM_ID = "fcmt-router-001";
    private static final String SOLUTION_ID = "scmt-router-001";
    private static final String REASON = "spam";

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin-id", null, Collections.emptyList()));
        when(forumModerator.getType()).thenReturn(FORUM_TYPE);
        when(solutionModerator.getType()).thenReturn(SOLUTION_TYPE);
        when(contestModerator.getType()).thenReturn(CONTEST_TYPE);
        router = new AdminCommentServiceImpl(
                List.of(forumModerator, solutionModerator, contestModerator),
                commentReadPort,
                currentUserProvider,
                new com.ulticode.modules.admin.bulk.AdminBulkExecutor());
        router.indexModeratorsByType();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("getComment routes to the moderator whose getType() matches the type param")
    void getComment_routesByType() {
        AdminCommentVO forumVo = stubVo(FORUM_ID, FORUM_TYPE);
        AdminCommentVO solutionVo = stubVo(SOLUTION_ID, SOLUTION_TYPE);
        when(forumModerator.getComment(FORUM_ID)).thenReturn(forumVo);
        when(solutionModerator.getComment(SOLUTION_ID)).thenReturn(solutionVo);

        assertSame(forumVo, router.getComment(FORUM_ID, FORUM_TYPE));
        assertSame(solutionVo, router.getComment(SOLUTION_ID, SOLUTION_TYPE));

        verify(forumModerator).getComment(FORUM_ID);
        verify(solutionModerator).getComment(SOLUTION_ID);
        verify(contestModerator, never()).getComment(any());
    }

    @Test
    @DisplayName("flagComment / unflagComment / deleteComment route by type and re-fetch via getComment")
    void mutators_routeByType() {
        AdminCommentVO forumVo = stubVo(FORUM_ID, FORUM_TYPE);
        AdminCommentVO solutionVo = stubVo(SOLUTION_ID, SOLUTION_TYPE);
        when(forumModerator.getComment(FORUM_ID)).thenReturn(forumVo);
        when(solutionModerator.getComment(SOLUTION_ID)).thenReturn(solutionVo);

        AdminCommentVO flagged = router.flagComment(FORUM_ID, FORUM_TYPE, REASON);
        assertSame(forumVo, flagged, "flagComment should return moderator's getComment result");
        verify(forumModerator).flagComment(FORUM_ID, REASON);
        verify(solutionModerator, never()).flagComment(any(), any());

        AdminCommentVO unflagged = router.unflagComment(FORUM_ID, FORUM_TYPE);
        assertSame(forumVo, unflagged);
        verify(forumModerator).unflagComment(FORUM_ID);
        verify(solutionModerator, never()).unflagComment(any());

        router.deleteComment(SOLUTION_ID, SOLUTION_TYPE);
        verify(solutionModerator).deleteComment(SOLUTION_ID);
        verify(forumModerator, never()).deleteComment(any());
    }

    @Test
    @DisplayName("Unknown type tag throws BusinessException before invoking any moderator")
    void unknownType_throwsBeforeMutation() {
        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> router.deleteComment(FORUM_ID, "post"));
        assertTrue(
                ex.getMessage() != null && ex.getMessage().contains("Invalid comment type"),
                "Error message should call out invalid type, was: " + ex.getMessage());

        verify(forumModerator, never()).deleteComment(any());
        verify(solutionModerator, never()).deleteComment(any());
        verify(contestModerator, never()).deleteComment(any());
    }

    @Test
    @DisplayName("Type=forum listComments goes to forum moderator with original page/limit")
    void listComments_routesByType() {
        AdminCommentQueryDTO query = new AdminCommentQueryDTO();
        query.setType(FORUM_TYPE);
        query.setPage(2);
        query.setLimit(15);
        PageResult<AdminCommentVO> expected = PageResult.of(List.of(stubVo(FORUM_ID, FORUM_TYPE)), 1L, 2, 15);
        when(forumModerator.listComments(query, 2, 15)).thenReturn(expected);

        PageResult<AdminCommentVO> actual = router.getComments(query);

        assertSame(expected, actual);
        verify(forumModerator).listComments(query, 2, 15);
        verify(solutionModerator, never()).listComments(any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("Type=null listComments iterates every moderator with bounded page size, then paginates in memory")
    void listComments_nullType_iteratesAllAndPaginates() {
        AdminCommentQueryDTO query = new AdminCommentQueryDTO();
        query.setType(null);
        query.setPage(1);
        query.setLimit(3);

        List<AdminCommentVO> forumItems = List.of(
            stubVo("f1", FORUM_TYPE, LocalDateTime.of(2026, 7, 1, 10, 0)),
            stubVo("f2", FORUM_TYPE, LocalDateTime.of(2026, 7, 3, 10, 0)));
        List<AdminCommentVO> solutionItems = List.of(
            stubVo("s1", SOLUTION_TYPE, LocalDateTime.of(2026, 7, 4, 10, 0)));
        List<AdminCommentVO> contestItems = List.of(
            stubVo("c1", CONTEST_TYPE, LocalDateTime.of(2026, 7, 2, 10, 0)),
            stubVo("c2", CONTEST_TYPE, LocalDateTime.of(2026, 7, 5, 10, 0)));

        // Bounded: page size 100, up to 4 pages. Each moderator returns all
        // items in page 1 (items < 100), so the loop stops after page 1.
        when(forumModerator.listComments(eq(query), eq(1), eq(100)))
            .thenReturn(PageResult.of(forumItems, (long) forumItems.size(), 1, 100));
        when(solutionModerator.listComments(eq(query), eq(1), eq(100)))
            .thenReturn(PageResult.of(solutionItems, (long) solutionItems.size(), 1, 100));
        when(contestModerator.listComments(eq(query), eq(1), eq(100)))
            .thenReturn(PageResult.of(contestItems, (long) contestItems.size(), 1, 100));

        PageResult<AdminCommentVO> merged = router.getComments(query);

        // 5 items total, page 1 limit 3 → first 3 by createdAt desc: c2, s1, f2
        assertEquals(5L, merged.getTotal());
        assertEquals(3, merged.getItems().size());
        assertEquals("c2", merged.getItems().get(0).id());
        assertEquals("s1", merged.getItems().get(1).id());
        assertEquals("f2", merged.getItems().get(2).id());

        verify(forumModerator, times(1)).listComments(eq(query), eq(1), eq(100));
        verify(solutionModerator, times(1)).listComments(eq(query), eq(1), eq(100));
        verify(contestModerator, times(1)).listComments(eq(query), eq(1), eq(100));
    }

    @Test
    @DisplayName("Type=null with empty moderator list returns empty PageResult")
    void listComments_emptyModerators_returnsEmpty() {
        AdminCommentServiceImpl emptyRouter = new AdminCommentServiceImpl(
                new ArrayList<>(), commentReadPort, currentUserProvider,
                new com.ulticode.modules.admin.bulk.AdminBulkExecutor());
        emptyRouter.indexModeratorsByType();

        AdminCommentQueryDTO query = new AdminCommentQueryDTO();
        query.setPage(1);
        query.setLimit(10);
        PageResult<AdminCommentVO> result = emptyRouter.getComments(query);

        assertEquals(0L, result.getTotal());
        assertTrue(result.getItems() == null || result.getItems().isEmpty());
        verify(forumModerator, never()).listComments(any(), anyInt(), anyInt());
    }

    private static AdminCommentVO stubVo(String id, String type) {
        return stubVo(id, type, LocalDateTime.of(2026, 7, 1, 12, 0));
    }

    private static AdminCommentVO stubVo(String id, String type, LocalDateTime createdAt) {
        return new AdminCommentVO(
            id, "body-" + id, createdAt, createdAt,
            "author-" + id, null, type, "parent-" + id, "parent-title",
            null, false, null, null, false, null, null);
    }
}
