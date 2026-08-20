package com.ulticode.modules.solution.port;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ulticode.app.api.dto.SolutionIndexDTO;
import com.ulticode.app.api.service.SolutionReadPort;
import com.ulticode.modules.solution.entity.Solution;
import com.ulticode.modules.solution.mapper.SolutionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
@Primary
@RequiredArgsConstructor
public class DefaultSolutionReadAdapter implements SolutionReadPort {

    private final SolutionMapper solutionMapper;

    @Override
    public List<SolutionIndexDTO> searchForIndex(String query, int limit) {
        return searchForIndex(query, 0, limit);
    }

    @Override
    public List<SolutionIndexDTO> searchForIndex(String query, int offset, int limit) {
        if (query == null || query.isBlank() || offset < 0 || limit <= 0) {
            return List.of();
        }
        QueryWrapper<Solution> wrapper = new QueryWrapper<>();
        wrapper.and(w -> w
                .like("title", query)
                .or()
                .like("summary", query)
        )
                .eq("is_published", true)
                .eq("is_deleted", false)
                .orderByAsc("id")
                .last("LIMIT " + limit + " OFFSET " + offset);

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
    public long countForIndex(String query) {
        if (query == null || query.isBlank()) {
            return 0;
        }
        QueryWrapper<Solution> wrapper = new QueryWrapper<>();
        wrapper.and(w -> w
                .like("title", query)
                .or()
                .like("summary", query)
        )
                .eq("is_published", true)
                .eq("is_deleted", false);
        Long count = solutionMapper.selectCount(wrapper);
        return count == null ? 0 : count;
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

    @Override
    public Map<String, String> findTitlesByIds(Set<String> solutionIds) {
        if (solutionIds.isEmpty()) {
            return Map.of();
        }
        Map<String, String> result = new HashMap<>();
        // selectBatchIds respects MyBatis-Plus logical-delete filtering;
        // null titles are preserved so callers can distinguish "missing"
        // from "present with null title".
        for (Solution s : solutionMapper.selectBatchIds(solutionIds)) {
            result.put(s.getId(), s.getTitle());
        }
        return result;
    }
}
