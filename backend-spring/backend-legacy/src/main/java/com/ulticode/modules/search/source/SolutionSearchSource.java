package com.ulticode.modules.search.source;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ulticode.modules.search.dto.SearchIndexType;
import com.ulticode.modules.search.dto.SearchResponseVO;
import com.ulticode.modules.solution.entity.Solution;
import com.ulticode.modules.solution.mapper.SolutionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Search source for the solution (editorial) domain. Owns:
 * <ul>
 *   <li>The {@link SolutionMapper} call and the {@code is_published} /
 *       {@code is_deleted} predicates that gate published, non-deleted
 *       editorials.</li>
 *   <li>The title / summary LIKE matching and the LIMIT cap.</li>
 *   <li>The canonical {@code /problems/{problemId}/solutions/{id}} URL
 *       (with a {@code /solutions/{id}} fallback when the parent problem
 *       id is unknown).</li>
 * </ul>
 *
 * @author ulticode
 */
@Service
@RequiredArgsConstructor
public class SolutionSearchSource implements SearchSource {

    private final SolutionMapper solutionMapper;

    @Override
    public SearchIndexType getIndexType() {
        return SearchIndexType.SOLUTIONS;
    }

    @Override
    public List<SearchResponseVO.SearchResultItem> searchDatabase(String query, int offset, int limit) {
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

        List<SearchResponseVO.SearchResultItem> results = new ArrayList<>(solutions.size());
        for (Solution solution : solutions) {
            results.add(SearchResponseVO.SearchResultItem.builder()
                    .id(solution.getId())
                    .type(SearchIndexType.SOLUTIONS.name())
                    .title(solution.getTitle())
                    .description(solution.getSummary())
                    .url(buildSolutionUrl(solution))
                    .build());
        }
        return results;
    }

    @Override
    public String buildUrl(String entityId) {
        return "/solutions/" + entityId;
    }

    /**
     * Build the rich solution URL. The database row carries the parent
     * problem id, so the canonical {@code /problems/{problemId}/solutions/{id}}
     * shape is used whenever the parent is known; otherwise the simple
     * fallback {@code /solutions/{id}} applies.
     */
    private String buildSolutionUrl(Solution solution) {
        if (solution.getProblemId() != null) {
            return "/problems/" + solution.getProblemId() + "/solutions/" + solution.getId();
        }
        return buildUrl(solution.getId());
    }
}