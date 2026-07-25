package com.ulticode.modules.solution.service.impl;

import com.ulticode.modules.solution.dto.SolutionTopicVO;
import com.ulticode.modules.solution.mapper.SolutionTopicMapper;
import com.ulticode.modules.solution.service.SolutionTopicService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Read-only solution topic service.
 * Returns topic list aggregated from solution_topics table.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SolutionTopicServiceImpl implements SolutionTopicService {

    private final SolutionTopicMapper solutionTopicMapper;

    @Override
    public List<SolutionTopicVO> listTopics() {
        // L2 (review): include query shape + result count for log triage.
        // Even though debug-level is gated, the SLF4J best practice (per
        // 02-exception-logging.md) is to log complete context once when the
        // operation succeeds — not just the trigger phrase.
        final String queryShape = "WHERE is_active=1 AND is_deleted=0 ORDER BY sort_order";
        List<Map<String, Object>> rows = solutionTopicMapper.listActiveTopicsWithCount();
        List<SolutionTopicVO> vos = rows.stream()
                .map(this::toVO)
                .collect(Collectors.toList());
        log.debug("listTopics ok: query={}, returnedCount={}", queryShape, vos.size());
        return vos;
    }

    private SolutionTopicVO toVO(Map<String, Object> row) {
        // Use HashMap-safe accessor: rows come from MyBatis as Map<String, Object>
        // where solution_count is a primitive 0 (boxed Integer). Defensive cast
        // for the unlikely case of BigDecimal/Long from other dialects.
        Object countRaw = row.get("solution_count");
        int count = (countRaw instanceof Number n) ? n.intValue() : 0;
        return SolutionTopicVO.builder()
                .id((String) row.get("id"))
                .name((String) row.get("name"))
                .count(count)
                .build();
    }
}
