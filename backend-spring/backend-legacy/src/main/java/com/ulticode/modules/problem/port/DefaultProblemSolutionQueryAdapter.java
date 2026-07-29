package com.ulticode.modules.problem.port;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ulticode.modules.solution.entity.Solution;
import com.ulticode.modules.solution.mapper.SolutionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Default adapter for {@link ProblemSolutionQueryPort} backed by
 * {@code SolutionMapper}. Lives in backend-legacy; when the problem module
 * migrates to backend-app, this adapter is replaced or the underlying data
 * access moves with it.
 *
 * @author ulticode
 */
@Component
@RequiredArgsConstructor
public class DefaultProblemSolutionQueryAdapter implements ProblemSolutionQueryPort {

    private final SolutionMapper solutionMapper;

    @Override
    public long countSolutionsByProblemId(Long problemId) {
        Long count = solutionMapper.selectCount(
                new LambdaQueryWrapper<Solution>()
                        .eq(Solution::getProblemId, problemId));
        return count != null ? count : 0L;
    }
}
