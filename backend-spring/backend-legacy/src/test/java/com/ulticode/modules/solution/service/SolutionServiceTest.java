package com.ulticode.modules.solution.service;

import com.ulticode.common.uuid.UuidGenerator;
import com.ulticode.modules.problem.entity.Problem;
import com.ulticode.modules.problem.mapper.ProblemMapper;
import com.ulticode.modules.solution.dto.CreateSolutionDTO;
import com.ulticode.modules.solution.entity.Solution;
import com.ulticode.modules.solution.mapper.SolutionCommentMapper;
import com.ulticode.modules.solution.mapper.SolutionMapper;
import com.ulticode.modules.solution.port.ProblemExistencePort;
import com.ulticode.modules.solution.projection.SolutionProjection;
import com.ulticode.modules.solution.service.impl.SolutionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for SolutionService, focused on the fixes from
 * docs/api-tests/solution-api-test-plan.md (BUG-3, OBS-3).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SolutionServiceTest {

    @Mock
    private SolutionMapper solutionMapper;
    @Mock
    private SolutionCommentMapper solutionCommentMapper;
    @Mock
    private ProblemMapper problemMapper;
    @Mock
    private ProblemExistencePort problemExistencePort;
    @Mock
    private SolutionProjection solutionProjection;
    @Mock
    private Clock clock;
    @Mock
    private UuidGenerator uuidGenerator;

    @InjectMocks
    private SolutionServiceImpl solutionService;

    @BeforeEach
    void setUp() {
        lenient().when(clock.instant()).thenReturn(Instant.parse("2026-01-01T00:00:00Z"));
        lenient().when(clock.getZone()).thenReturn(ZoneId.of("UTC"));
        // Default: problem exists. create() tests rely on this.
        lenient().when(problemExistencePort.exists(any(Long.class))).thenReturn(true);
    }

    private static final String SOLUTION_ID = "sol-uuid-1";
    private static final String USER_ID = "user-uuid-1";
    private static final long PROBLEM_ID = 4L;

    private Solution existingSolution(Integer views) {
        Solution s = new Solution();
        s.setId(SOLUTION_ID);
        s.setProblemId(PROBLEM_ID);
        s.setUserId(USER_ID);
        s.setTitle("t");
        s.setContent("c");
        s.setSummary("");
        s.setLanguage("java");
        s.setTags("");
        s.setViews(views);
        s.setLikes(0);
        s.setDislikes(0);
        return s;
    }

    @Test
    @DisplayName("BUG-3: recordView is silent (no exception, no mapper call) when solution not found")
    void recordView_silentWhenSolutionNotFound() {
        when(solutionMapper.selectById(SOLUTION_ID)).thenReturn(null);

        solutionService.recordView(SOLUTION_ID, USER_ID);

        verify(solutionMapper, never()).updateById(any(Solution.class));
    }

    @Test
    @DisplayName("BUG-3: recordView increments views by 1 when solution exists")
    void recordView_incrementsViewsWhenFound() {
        Solution s = existingSolution(7);
        when(solutionMapper.selectById(SOLUTION_ID)).thenReturn(s);

        solutionService.recordView(SOLUTION_ID, USER_ID);

        ArgumentCaptor<Solution> captor = ArgumentCaptor.forClass(Solution.class);
        verify(solutionMapper).updateById(captor.capture());
        assertEquals(8, captor.getValue().getViews());
    }

    @Test
    @DisplayName("BUG-3: recordView treats null views as 0 and increments to 1")
    void recordView_nullViewsTreatedAsZero() {
        Solution s = existingSolution(null);
        when(solutionMapper.selectById(SOLUTION_ID)).thenReturn(s);

        solutionService.recordView(SOLUTION_ID, USER_ID);

        ArgumentCaptor<Solution> captor = ArgumentCaptor.forClass(Solution.class);
        verify(solutionMapper).updateById(captor.capture());
        assertEquals(1, captor.getValue().getViews());
    }

    @Test
    @DisplayName("OBS-3: create persists tags as comma-joined string (DTO List<String> -> entity String)")
    void create_persistsTagsAsJoinedString() {
        Problem problem = new Problem();
        problem.setId(PROBLEM_ID);
        problem.setHasSolution(false);
        when(problemMapper.selectById(PROBLEM_ID)).thenReturn(problem);
        when(solutionMapper.selectOne(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(null);

        CreateSolutionDTO dto = new CreateSolutionDTO();
        dto.setTitle("title");
        dto.setContent("content body");
        dto.setLanguage("java");
        dto.setTags(Arrays.asList("dp", "array"));

        solutionService.create(PROBLEM_ID, USER_ID, dto);

        ArgumentCaptor<Solution> captor = ArgumentCaptor.forClass(Solution.class);
        verify(solutionMapper).insert(captor.capture());
        assertEquals("dp,array", captor.getValue().getTags());
    }

    @Test
    @DisplayName("OBS-3: create persists empty string when tags is null")
    void create_persistsEmptyStringWhenTagsMissing() {
        Problem problem = new Problem();
        problem.setId(PROBLEM_ID);
        problem.setHasSolution(false);
        when(problemMapper.selectById(PROBLEM_ID)).thenReturn(problem);
        when(solutionMapper.selectOne(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(null);

        CreateSolutionDTO dto = new CreateSolutionDTO();
        dto.setTitle("t");
        dto.setContent("c");
        dto.setLanguage("java");
        // tags not set (null)

        solutionService.create(PROBLEM_ID, USER_ID, dto);

        ArgumentCaptor<Solution> captor = ArgumentCaptor.forClass(Solution.class);
        verify(solutionMapper).insert(captor.capture());
        assertEquals("", captor.getValue().getTags());
    }
}
