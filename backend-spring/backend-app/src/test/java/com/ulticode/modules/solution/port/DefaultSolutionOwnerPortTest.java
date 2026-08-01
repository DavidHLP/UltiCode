package com.ulticode.modules.solution.port;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ulticode.app.api.service.ProblemExistencePort;
import com.ulticode.app.api.service.SolutionOwnerPort;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.modules.solution.entity.Solution;
import com.ulticode.modules.solution.mapper.SolutionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultSolutionOwnerPortTest {

    @Mock
    private SolutionMapper solutionMapper;

    @Mock
    private ProblemExistencePort problemExistencePort;

    private DefaultSolutionOwnerPort port;

    @BeforeEach
    void setUp() {
        port = new DefaultSolutionOwnerPort(solutionMapper, problemExistencePort);
    }

    private Solution createSolution(String id, String userId, Long problemId) {
        Solution s = new Solution();
        s.setId(id);
        s.setUserId(userId);
        s.setProblemId(problemId);
        s.setTitle("Test Solution");
        s.setIsFlagged(false);
        s.setFlaggedReason("");
        return s;
    }

    @Test
    @DisplayName("flagSolution updates isFlagged and returns audit details")
    void flagSolution_success() {
        Solution solution = createSolution("s1", "u1", 100L);
        when(solutionMapper.selectById("s1")).thenReturn(solution);

        LocalDateTime now = LocalDateTime.now();
        SolutionOwnerPort.FlagResult result = port.flagSolution("s1", "spam", now);

        assertThat(result.authorUserId()).isEqualTo("u1");
        assertThat(result.oldIsFlagged()).isFalse();

        ArgumentCaptor<Solution> captor = ArgumentCaptor.forClass(Solution.class);
        verify(solutionMapper).updateById(captor.capture());
        assertThat(captor.getValue().getIsFlagged()).isTrue();
        assertThat(captor.getValue().getFlaggedReason()).isEqualTo("spam");
    }

    @Test
    @DisplayName("unflagSolution updates isFlagged=false and returns audit details")
    void unflagSolution_success() {
        Solution solution = createSolution("s1", "u1", 100L);
        solution.setIsFlagged(true);
        solution.setFlaggedReason("spam");
        when(solutionMapper.selectById("s1")).thenReturn(solution);

        SolutionOwnerPort.FlagResult result = port.unflagSolution("s1");

        assertThat(result.authorUserId()).isEqualTo("u1");
        assertThat(result.oldIsFlagged()).isTrue();
        assertThat(result.oldFlaggedReason()).isEqualTo("spam");

        ArgumentCaptor<Solution> captor = ArgumentCaptor.forClass(Solution.class);
        verify(solutionMapper).updateById(captor.capture());
        assertThat(captor.getValue().getIsFlagged()).isFalse();
    }

    @Test
    @DisplayName("deleteSolution deletes row and calls problemExistencePort.markHasSolution(100L, false) if 0 remaining")
    void deleteSolution_resetsProblemHasSolutionWhenEmpty() {
        Solution solution = createSolution("s1", "u1", 100L);
        when(solutionMapper.selectById("s1")).thenReturn(solution);
        when(solutionMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        SolutionOwnerPort.DeleteResult result = port.deleteSolution("s1");

        assertThat(result.authorUserId()).isEqualTo("u1");
        assertThat(result.title()).isEqualTo("Test Solution");
        assertThat(result.problemId()).isEqualTo(100L);

        verify(solutionMapper).deleteById("s1");
        verify(problemExistencePort).markHasSolution(100L, false);
    }

    @Test
    @DisplayName("flagSolution throws NOT_FOUND when solution missing")
    void flagSolution_notFound() {
        when(solutionMapper.selectById("missing")).thenReturn(null);

        assertThatThrownBy(() -> port.flagSolution("missing", "reason", LocalDateTime.now()))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.SOLUTION_NOT_FOUND.getCode());
    }
}
