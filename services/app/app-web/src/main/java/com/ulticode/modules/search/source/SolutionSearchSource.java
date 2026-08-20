package com.ulticode.modules.search.source;

import com.ulticode.app.api.dto.SolutionIndexDTO;
import com.ulticode.app.api.service.SolutionReadPort;
import com.ulticode.modules.search.dto.SearchIndexType;
import com.ulticode.modules.search.dto.SearchResponseVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Search source for the solution (editorial) domain. Owns:
 * <ul>
 *   <li>The search query delegation to {@link SolutionReadPort} which
 *       handles the {@code is_published} / {@code is_deleted} predicates
 *       and LIKE matching internally.</li>
 *   <li>The canonical {@code /problems/{problemId}/solutions/{id}} URL
 *       (with a {@code /solutions/{id}} fallback when the parent problem
 *       id is unknown).</li>
 * </ul>
 *
 * <p>P7-RELOCATE-SOLUTION-001: cut over from direct {@code SolutionMapper}
 * to {@code SolutionReadPort} so this source no longer imports the
 * solution entity or mapper.
 *
 * @author ulticode
 */
@Service
@RequiredArgsConstructor
public class SolutionSearchSource implements SearchSource {

    private final SolutionReadPort solutionReadPort;

    @Override
    public SearchIndexType getIndexType() {
        return SearchIndexType.SOLUTIONS;
    }

    @Override
    public List<SearchResponseVO.SearchResultItem> searchDatabase(String query, int offset, int limit) {
        List<SolutionIndexDTO> solutions = solutionReadPort.searchForIndex(query, offset, limit);

        List<SearchResponseVO.SearchResultItem> results = new ArrayList<>(solutions.size());
        for (SolutionIndexDTO solution : solutions) {
            results.add(SearchResponseVO.SearchResultItem.builder()
                    .id(solution.id())
                    .type(SearchIndexType.SOLUTIONS.name())
                    .title(solution.title())
                    .description(solution.summary())
                    .url(buildSolutionUrl(solution))
                    .build());
        }
        return results;
    }

    @Override
    public long countDatabase(String query) {
        return solutionReadPort.countForIndex(query);
    }

    @Override
    public String buildUrl(String entityId) {
        return "/solutions/" + entityId;
    }

    /**
     * Build the rich solution URL. The DTO carries the parent problem
     * id, so the canonical {@code /problems/{problemId}/solutions/{id}}
     * shape is used whenever the parent is known; otherwise the simple
     * fallback {@code /solutions/{id}} applies.
     */
    private String buildSolutionUrl(SolutionIndexDTO solution) {
        if (solution.problemId() != null) {
            return "/problems/" + solution.problemId() + "/solutions/" + solution.id();
        }
        return buildUrl(solution.id());
    }
}
