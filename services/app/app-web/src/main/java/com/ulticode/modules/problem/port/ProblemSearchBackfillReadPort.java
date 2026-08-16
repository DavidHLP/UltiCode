package com.ulticode.modules.problem.port;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ulticode.modules.problem.entity.Problem;
import com.ulticode.modules.problem.mapper.ProblemMapper;
import com.ulticode.modules.search.backfill.SearchBackfillDocument;
import com.ulticode.modules.search.backfill.SearchBackfillReadPort;
import com.ulticode.modules.search.dto.SearchIndexType;
import com.ulticode.modules.search.source.SearchDocumentBuilders;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * SEARCH-003 problem backfill enumeration (DEC-017).
 *
 * <p>Predicate mirrors the Q-read seam ({@code DefaultProblemSearchReadPort}:
 * published, non-deleted). Version is the row's {@code updated_at} epoch
 * millis (auto-maintained by MySQL ON UPDATE).
 */
@Component
@RequiredArgsConstructor
public class ProblemSearchBackfillReadPort implements SearchBackfillReadPort {

    private final ProblemMapper problemMapper;

    @Override
    public SearchIndexType type() {
        return SearchIndexType.PROBLEMS;
    }

    @Override
    public List<SearchBackfillDocument> enumerateForBackfill(int offset, int limit) {
        QueryWrapper<Problem> wrapper = new QueryWrapper<>();
        wrapper.select("id", "title", "slug", "difficulty", "updated_at")
                .eq("is_published", true)
                .eq("is_deleted", false)
                .orderByAsc("id")
                .last("LIMIT " + limit + " OFFSET " + offset);
        return problemMapper.selectList(wrapper).stream()
                .map(p -> new SearchBackfillDocument(
                        String.valueOf(p.getId()),
                        SearchBackfillReadPort.toVersionMillis(p.getUpdatedAt()),
                        SearchDocumentBuilders.problem(p)))
                .toList();
    }
}
