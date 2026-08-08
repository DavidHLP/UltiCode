package com.ulticode.modules.solution.projection;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ulticode.common.auth.CurrentUserProvider;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.response.PageResult;
import com.ulticode.modules.solution.dto.SolutionCommentVO;
import com.ulticode.modules.solution.dto.SolutionListItemVO;
import com.ulticode.modules.solution.dto.SolutionVO;
import com.ulticode.modules.solution.entity.Solution;
import com.ulticode.modules.solution.entity.SolutionComment;
import com.ulticode.modules.solution.mapper.SolutionCommentMapper;
import com.ulticode.modules.solution.mapper.SolutionMapper;
import com.ulticode.app.api.service.AchievementBadgeReadPort;
import com.ulticode.app.api.service.ProblemTagReadPort;
import com.ulticode.app.api.service.SolutionVoteReadPort;
import com.ulticode.modules.solution.port.SolutionUserReadPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DefaultSolutionProjection}.
 *
 * <p>Cross-domain reads go through consumer-owned ports, so this test
 * only mocks the ports — never the provider mappers. That is the
 * regression guarantee for the deepening: if anyone re-introduces a
 * direct mapper import, this file would need to grow mocks for it.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DefaultSolutionProjectionTest {

    @Mock
    private SolutionMapper solutionMapper;
    @Mock
    private SolutionCommentMapper solutionCommentMapper;
    @Mock
    private SolutionUserReadPort userReadPort;
    @Mock
    private SolutionVoteReadPort voteReadPort;
    @Mock
    private ProblemTagReadPort problemTagReadPort;
    @Mock
    private AchievementBadgeReadPort achievementBadgeReadPort;
    @Mock
    private CurrentUserProvider currentUserProvider;

    @InjectMocks
    private DefaultSolutionProjection projection;

    private static final String SOLUTION_ID = "sol-uuid-1";
    private static final String USER_ID = "user-uuid-1";
    private static final long PROBLEM_ID = 4L;

    @Test
    @DisplayName("toVO enriches author, vote counts, score, badges and parses comma-separated tags")
    void toVO_enrichesCountsAndTags() {
        Solution solution = new Solution();
        solution.setId(SOLUTION_ID);
        solution.setProblemId(PROBLEM_ID);
        solution.setUserId(USER_ID);
        solution.setTitle("t");
        solution.setTags("dp,array");

        var author = new SolutionUserReadPort.UserSummary(USER_ID, "Alice", "a.png");
        when(userReadPort.findById(USER_ID)).thenReturn(author);

        when(voteReadPort.countLikes(eq(SOLUTION_ID))).thenReturn(3L);
        when(voteReadPort.countDislikes(eq(SOLUTION_ID))).thenReturn(1L);
        when(solutionCommentMapper.countBySolutionId(SOLUTION_ID)).thenReturn(2L);
        when(problemTagReadPort.findFirstTagLabel(PROBLEM_ID)).thenReturn("Dynamic Programming");
        when(achievementBadgeReadPort.findBadgeNames(USER_ID, 3))
                .thenReturn(List.of("100-day-streak", "first-accepted"));

        SolutionVO vo = projection.toVO(solution);

        assertEquals("Alice", vo.getAuthorName());
        assertEquals(3L, vo.getLikes());
        assertEquals(1L, vo.getDislikes());
        assertEquals(2L, vo.getComments());
        assertEquals(2L, vo.getScore());
        assertEquals(List.of("dp", "array"), vo.getTags());
        assertEquals("Dynamic Programming", vo.getTopicName());
        assertEquals("100-day-streak", vo.getFlair());
        assertEquals(List.of("100-day-streak", "first-accepted"), vo.getBadges());
    }

    @Test
    @DisplayName("toVO uses UserReadProjection (no UserMapper in this test)")
    void toVO_usesUserReadProjection() {
        Solution solution = new Solution();
        solution.setId(SOLUTION_ID);
        solution.setProblemId(PROBLEM_ID);
        solution.setUserId(USER_ID);
        solution.setTags("");

        projection.toVO(solution);

        verify(userReadPort).findById(USER_ID);
    }

    @Test
    @DisplayName("toVO returns null for a null entity")
    void toVO_nullEntity() {
        assertNull(projection.toVO(null));
    }

    @Test
    @DisplayName("toVO leaves badge list null when the user has none")
    void toVO_noBadges() {
        Solution solution = new Solution();
        solution.setId(SOLUTION_ID);
        solution.setProblemId(PROBLEM_ID);
        solution.setUserId(USER_ID);
        solution.setTags("");

        when(achievementBadgeReadPort.findBadgeNames(USER_ID, 3)).thenReturn(Collections.emptyList());

        SolutionVO vo = projection.toVO(solution);

        assertNull(vo.getBadges());
        assertNull(vo.getFlair());
    }

    @Test
    @DisplayName("getComments throws SOLUTION_NOT_FOUND when the solution is missing")
    void getComments_throwsWhenSolutionMissing() {
        when(solutionMapper.selectById(SOLUTION_ID)).thenReturn(null);

        assertThrows(BusinessException.class, () -> projection.getComments(SOLUTION_ID));
    }

    @Test
    @DisplayName("getComments maps the ordered comment list to VOs")
    void getComments_mapsComments() {
        when(solutionMapper.selectById(SOLUTION_ID)).thenReturn(new Solution());

        SolutionComment c1 = new SolutionComment();
        c1.setId("c1");
        c1.setSolutionId(SOLUTION_ID);
        c1.setContent("first");
        c1.setCreatedAt(LocalDateTime.now());
        SolutionComment c2 = new SolutionComment();
        c2.setId("c2");
        c2.setSolutionId(SOLUTION_ID);
        c2.setContent("second");
        c2.setCreatedAt(LocalDateTime.now());
        when(solutionCommentMapper.selectList(any())).thenReturn(List.of(c1, c2));

        List<SolutionCommentVO> vos = projection.getComments(SOLUTION_ID);

        assertEquals(2, vos.size());
        assertEquals("c1", vos.get(0).getId());
        assertEquals("c2", vos.get(1).getId());
    }

    @Test
    @DisplayName("findByProblemId returns an empty page without batch enrichment when no solutions exist")
    void findByProblemId_emptyPageShortCircuits() {
        Page<Solution> emptyPage = new Page<>(1, 20);
        when(solutionMapper.selectPage(any(), any(Wrapper.class))).thenReturn(emptyPage);

        PageResult<SolutionListItemVO> result = projection.findByProblemId(PROBLEM_ID, 1, 20);

        assertEquals(0L, result.getTotal());
    }

    @Test
    @DisplayName("findByProblemId uses one batched user fetch (kills the per-row N+1)")
    void findByProblemId_batchedUserFetch() {
        Solution a = new Solution();
        a.setId("s1");
        a.setProblemId(PROBLEM_ID);
        a.setUserId("u1");
        a.setIsPublished(true);
        a.setTags("");
        Solution b = new Solution();
        b.setId("s2");
        b.setProblemId(PROBLEM_ID);
        b.setUserId("u2");
        b.setIsPublished(true);
        b.setTags("");
        Page<Solution> page = new Page<>(1, 20);
        page.setRecords(List.of(a, b));
        page.setTotal(2L);
        when(solutionMapper.selectPage(any(), any(Wrapper.class))).thenReturn(page);

        when(userReadPort.findAllById(any())).thenReturn(Map.of(
                "u1", user("u1", "Alice"),
                "u2", user("u2", "Bob")));
        when(voteReadPort.countLikesByTargets(any())).thenReturn(Map.of());
        when(voteReadPort.countDislikesByTargets(any())).thenReturn(Map.of());
        when(currentUserProvider.getCurrentUserId()).thenReturn(null);

        PageResult<SolutionListItemVO> result = projection.findByProblemId(PROBLEM_ID, 1, 20);

        verify(userReadPort).findAllById(any());
        assertEquals(2, result.getItems().size());
        assertEquals("Alice", result.getItems().get(0).getAuthor().getName());
        assertEquals("Bob", result.getItems().get(1).getAuthor().getName());
    }

    @Test
    @DisplayName("toVO with viewerId consults the vote port for the user's vote state")
    void toVO_viewerVote() {
        Solution solution = new Solution();
        solution.setId(SOLUTION_ID);
        solution.setProblemId(PROBLEM_ID);
        solution.setUserId(USER_ID);
        solution.setTags("");

        when(voteReadPort.viewerVotes(eq("viewer-1"), any()))
                .thenReturn(Map.of(SOLUTION_ID, 1));

        SolutionVO vo = projection.toVO(solution, "viewer-1");

        assertEquals(1, vo.getUserVote());
    }

    private static SolutionUserReadPort.UserSummary user(String id, String name) {
        return new SolutionUserReadPort.UserSummary(id, name, "avatar-" + id + ".png");
    }
}