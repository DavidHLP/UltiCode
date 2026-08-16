package com.ulticode.modules.solution.port;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ulticode.modules.search.backfill.SearchBackfillDocument;
import com.ulticode.modules.search.backfill.SearchBackfillReadPort;
import com.ulticode.modules.search.dto.SearchIndexType;
import com.ulticode.modules.search.source.SearchDocumentBuilders;
import com.ulticode.modules.solution.entity.Solution;
import com.ulticode.modules.solution.mapper.SolutionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * SEARCH-003 solution backfill enumeration (DEC-017).
 *
 * <p>Predicate mirrors the Q-read seam ({@code DefaultSolutionReadAdapter}:
 * published, non-deleted). Version is the row's {@code updated_at} epoch
 * millis (V20260816220000 added MySQL ON UPDATE so edits maintain it).
 */
@Component
@RequiredArgsConstructor
public class SolutionSearchBackfillReadPort implements SearchBackfillReadPort {

    private final SolutionMapper solutionMapper;

    @Override
    public SearchIndexType type() {
        return SearchIndexType.SOLUTIONS;
    }

    @Override
    public List<SearchBackfillDocument> enumerateForBackfill(int offset, int limit) {
        QueryWrapper<Solution> wrapper = new QueryWrapper<>();
        wrapper.select("id", "title", "summary", "problem_id", "updated_at")
                .eq("is_published", true)
                .eq("is_deleted", false)
                .orderByAsc("id")
                .last("LIMIT " + limit + " OFFSET " + offset);
        return solutionMapper.selectList(wrapper).stream()
                .map(s -> new SearchBackfillDocument(
                        s.getId(),
                        SearchBackfillReadPort.toVersionMillis(s.getUpdatedAt()),
                        SearchDocumentBuilders.solution(s)))
                .toList();
    }
}
