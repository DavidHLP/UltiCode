package com.ulticode.modules.search.source;

import com.ulticode.app.api.dto.ProblemIndexDTO;
import com.ulticode.modules.problem.port.ProblemSearchReadPort;
import com.ulticode.modules.search.dto.SearchIndexType;
import com.ulticode.modules.search.dto.SearchResponseVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Search source for the problem domain. Owns:
 * <ul>
 *   <li>The {@link ProblemSearchReadPort} call for fetching problem index data.</li>
 *   <li>The {@code /problems/{slug}} URL template.</li>
 *   <li>The problem metadata projection (slug, difficulty).</li>
 * </ul>
 *
 * @author ulticode
 */
@Service
@RequiredArgsConstructor
public class ProblemSearchSource implements SearchSource {

    private final ProblemSearchReadPort problemSearchReadPort;

    @Override
    public SearchIndexType getIndexType() {
        return SearchIndexType.PROBLEMS;
    }

    @Override
    public List<SearchResponseVO.SearchResultItem> searchDatabase(String query, int offset, int limit) {
        List<ProblemIndexDTO> problems = problemSearchReadPort.searchForIndex(query, offset, limit);

        return problems.stream()
                .map(dto -> SearchResponseVO.SearchResultItem.builder()
                        .id(dto.id())
                        .type(SearchIndexType.PROBLEMS.name())
                        .title(dto.title())
                        .description(dto.slug())
                        .url(buildUrl(dto.slug()))
                        .metadata(createProblemMetadata(dto))
                        .build())
                .toList();
    }

    @Override
    public long countDatabase(String query) {
        return problemSearchReadPort.countForIndex(query);
    }

    @Override
    public String buildUrl(String entityId) {
        return "/problems/" + entityId;
    }

    private Map<String, Object> createProblemMetadata(ProblemIndexDTO dto) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("slug", dto.slug());
        if (dto.difficulty() != null) {
            metadata.put("difficulty", dto.difficulty());
        }
        return metadata;
    }
}
