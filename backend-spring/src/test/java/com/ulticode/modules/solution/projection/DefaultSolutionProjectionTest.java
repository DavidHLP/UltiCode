package com.ulticode.modules.solution.projection;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.response.PageResult;
import com.ulticode.modules.achievement.mapper.AchievementMapper;
import com.ulticode.modules.achievement.mapper.UserAchievementMapper;
import com.ulticode.modules.problem.mapper.ProblemTagMapper;
import com.ulticode.modules.problem.mapper.ProblemTagRelationMapper;
import com.ulticode.modules.solution.dto.SolutionCommentVO;
import com.ulticode.modules.solution.dto.SolutionListItemVO;
import com.ulticode.modules.solution.dto.SolutionVO;
import com.ulticode.modules.solution.entity.Solution;
import com.ulticode.modules.solution.entity.SolutionComment;
import com.ulticode.modules.solution.mapper.SolutionCommentMapper;
import com.ulticode.modules.solution.mapper.SolutionMapper;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.user.mapper.UserMapper;
import com.ulticode.modules.vote.entity.enums.EdgeOperationTargetType;
import com.ulticode.modules.vote.entity.enums.EdgeOperationType;
import com.ulticode.modules.vote.mapper.EdgeOperationMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the read-side projection lifted out of {@code SolutionServiceImpl}: entity-to-VO
 * enrichment ({@link DefaultSolutionProjection#toVO}), the comment read guard/ordering
 * ({@link DefaultSolutionProjection#getComments}) and the empty-page short-circuit of
 * {@link DefaultSolutionProjection#findByProblemId}.
 */
@ExtendWith(MockitoExtension.class)
class DefaultSolutionProjectionTest {

    @Mock
    private SolutionMapper solutionMapper;
    @Mock
    private SolutionCommentMapper solutionCommentMapper;
    @Mock
    private UserMapper userMapper;
    @Mock
    private EdgeOperationMapper edgeOperationMapper;
    @Mock
    private ProblemTagRelationMapper problemTagRelationMapper;
    @Mock
    private ProblemTagMapper problemTagMapper;
    @Mock
    private UserAchievementMapper userAchievementMapper;
    @Mock
    private AchievementMapper achievementMapper;

    @InjectMocks
    private DefaultSolutionProjection projection;

    private static final String SOLUTION_ID = "sol-uuid-1";
    private static final String USER_ID = "user-uuid-1";
    private static final long PROBLEM_ID = 4L;

    private static final String TARGET_TYPE = EdgeOperationTargetType.SOLUTION.getValue();
    private static final String VOTE_UP = EdgeOperationType.VOTE_UP.getValue();
    private static final String VOTE_DOWN = EdgeOperationType.VOTE_DOWN.getValue();

    @Test
    @DisplayName("toVO enriches author, vote counts, score and parses comma-separated tags")
    void toVO_enrichesCountsAndTags() {
        Solution solution = new Solution();
        solution.setId(SOLUTION_ID);
        solution.setProblemId(PROBLEM_ID);
        solution.setUserId(USER_ID);
        solution.setTitle("t");
        solution.setTags("dp,array");

        User author = new User();
        author.setId(USER_ID);
        author.setName("Alice");
        author.setAvatar("a.png");
        when(userMapper.selectById(USER_ID)).thenReturn(author);

        when(edgeOperationMapper.countByTargetAndOperation(SOLUTION_ID, TARGET_TYPE, VOTE_UP)).thenReturn(3);
        when(edgeOperationMapper.countByTargetAndOperation(SOLUTION_ID, TARGET_TYPE, VOTE_DOWN)).thenReturn(1);
        when(solutionCommentMapper.countBySolutionId(SOLUTION_ID)).thenReturn(2L);
        when(problemTagRelationMapper.findTagIdsByProblemId(PROBLEM_ID)).thenReturn(Collections.emptyList());
        when(userAchievementMapper.findByUserId(USER_ID)).thenReturn(Collections.emptyList());

        SolutionVO vo = projection.toVO(solution);

        assertEquals("Alice", vo.getAuthorName());
        assertEquals(3L, vo.getLikes());
        assertEquals(1L, vo.getDislikes());
        assertEquals(2L, vo.getComments());
        assertEquals(2L, vo.getScore());
        assertEquals(List.of("dp", "array"), vo.getTags());
    }

    @Test
    @DisplayName("toVO returns null for a null entity")
    void toVO_nullEntity() {
        assertNull(projection.toVO(null));
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
}
