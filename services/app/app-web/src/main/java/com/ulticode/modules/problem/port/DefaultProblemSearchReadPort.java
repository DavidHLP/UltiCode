package com.ulticode.modules.problem.port;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ulticode.app.api.dto.ProblemIndexDTO;
import com.ulticode.modules.problem.entity.Problem;
import com.ulticode.modules.problem.mapper.ProblemMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class DefaultProblemSearchReadPort implements ProblemSearchReadPort {

    private final ProblemMapper problemMapper;

    @Override
    public List<ProblemIndexDTO> searchForIndex(String query, int offset, int limit) {
        if (query == null || query.isBlank() || offset < 0 || limit <= 0) {
            return List.of();
        }
        QueryWrapper<Problem> wrapper = new QueryWrapper<>();
        wrapper.eq("is_published", true)
                .eq("is_deleted", false)
                .and(w -> w.like("title", query).or().like("slug", query))
                .orderByAsc("id")
                .last("LIMIT " + limit + " OFFSET " + offset);
        List<Problem> problems = problemMapper.selectList(wrapper);
        if (problems == null || problems.isEmpty()) {
            return List.of();
        }
        return problems.stream()
                .map(p -> new ProblemIndexDTO(String.valueOf(p.getId()), p.getTitle(), p.getSlug(), p.getDifficulty()))
                .toList();
    }

    public List<ProblemIndexDTO> searchForIndex(String query, int limit) {
        return searchForIndex(query, 0, limit);
    }

    @Override
    public long countForIndex(String query) {
        if (query == null || query.isBlank()) {
            return 0;
        }
        QueryWrapper<Problem> wrapper = new QueryWrapper<>();
        wrapper.eq("is_published", true)
                .eq("is_deleted", false)
                .and(w -> w.like("title", query).or().like("slug", query));
        Long count = problemMapper.selectCount(wrapper);
        return count == null ? 0 : count;
    }
}
