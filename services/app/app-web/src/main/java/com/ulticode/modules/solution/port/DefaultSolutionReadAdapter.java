package com.ulticode.modules.solution.port;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ulticode.app.api.dto.SolutionIndexDTO;
import com.ulticode.app.api.service.SolutionReadPort;
import com.ulticode.modules.solution.entity.Solution;
import com.ulticode.modules.solution.mapper.SolutionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Adapter implementing {@link SolutionReadPort} from backend-app-api.
 *
 * <p>Lives in the solution module (backend-legacy until the family relocates)
 * and delegates to {@link SolutionMapper}. External consumers (search, problem)
 * inject {@code SolutionReadPort} instead of the mapper directly.
 *
 * <p>P7-RELOCATE-SOLUTION-001: pre-paving step to decouple external consumers
 * from solution's mapper and entity types.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultSolutionReadAdapter implements SolutionReadPort {

    private final SolutionMapper solutionMapper;

    @Override
    public List<SolutionIndexDTO> searchForIndex(String query, int limit) {
        QueryWrapper<Solution> wrapper = new QueryWrapper<>();
        wrapper.and(w -> w
                .like("title", query)
                .or()
                .like("summary", query)
        )
                .eq("is_published", true)
                .eq("is_deleted", false)
                .last("LIMIT " + limit);

        List<Solution> solutions = solutionMapper.selectList(wrapper);

        return solutions.stream()
                .map(s -> new SolutionIndexDTO(
                        s.getId(),
                        s.getTitle(),
                        s.getSummary(),
                        s.getProblemId()))
                .toList();
    }

    @Override
    public long countByProblemId(Long problemId) {
        Long count = solutionMapper.selectCount(
                new QueryWrapper<Solution>()
                        .eq("problem_id", problemId));
        return count != null ? count : 0L;
    }

    @Override
    public long countByUserId(String userId) {
        Long count = solutionMapper.countByUserId(userId);
        return count != null ? count : 0L;
    }
}
