package com.ulticode.modules.search.source;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ulticode.modules.problem.entity.Problem;
import com.ulticode.modules.problem.mapper.ProblemMapper;
import com.ulticode.modules.search.dto.SearchIndexType;
import com.ulticode.modules.search.dto.SearchResponseVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Search source for the problem domain. Owns:
 * <ul>
 *   <li>The {@link ProblemMapper} call and the {@code is_published} /
 *       {@code is_deleted} predicates that gate published, non-deleted
 *       problems.</li>
 *   <li>The title / slug LIKE matching and the LIMIT cap.</li>
 *   <li>The {@code /problems/{slug}} URL template.</li>
 *   <li>The problem metadata projection (slug, difficulty).</li>
 * </ul>
 *
 * @author ulticode
 */
@Service
@RequiredArgsConstructor
public class ProblemSearchSource implements SearchSource {

    private final ProblemMapper problemMapper;

    @Override
    public SearchIndexType getIndexType() {
        return SearchIndexType.PROBLEMS;
    }

    @Override
    public List<SearchResponseVO.SearchResultItem> searchDatabase(String query, int offset, int limit) {
        QueryWrapper<Problem> wrapper = new QueryWrapper<>();
        wrapper.and(w -> w
                .like("title", query)
                .or()
                .like("slug", query)
        )
                .eq("is_published", true)
                .eq("is_deleted", false)
                .last("LIMIT " + limit);

        List<Problem> problems = problemMapper.selectList(wrapper);

        List<SearchResponseVO.SearchResultItem> results = new ArrayList<>(problems.size());
        for (Problem problem : problems) {
            results.add(SearchResponseVO.SearchResultItem.builder()
                    .id(String.valueOf(problem.getId()))
                    .type(SearchIndexType.PROBLEMS.name())
                    .title(problem.getTitle())
                    .description(problem.getSlug())
                    .url(buildUrl(problem.getSlug()))
                    .metadata(createProblemMetadata(problem))
                    .build());
        }
        return results;
    }

    @Override
    public String buildUrl(String entityId) {
        return "/problems/" + entityId;
    }

    private Map<String, Object> createProblemMetadata(Problem problem) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("slug", problem.getSlug());
        if (problem.getDifficulty() != null) {
            metadata.put("difficulty", problem.getDifficulty());
        }
        return metadata;
    }
}