package com.ulticode.modules.problem.port;

import com.ulticode.app.api.service.ProblemTagStatsReadPort;
import com.ulticode.modules.problem.mapper.ProblemTagRelationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Production adapter for {@link ProblemTagStatsReadPort}.
 * Delegates to {@link ProblemTagRelationMapper} for the actual query.
 *
 * <p>Non-throwing: returns an empty list when data access fails.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultProblemTagStatsReadPort implements ProblemTagStatsReadPort {

    private final ProblemTagRelationMapper problemTagRelationMapper;

    @Override
    public List<Map<String, Object>> findTagStatsByUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            return List.of();
        }
        try {
            List<Map<String, Object>> result = problemTagRelationMapper.findTagStatsByUserId(userId);
            return result != null ? result : List.of();
        } catch (Exception e) {
            log.warn("Failed to read tag stats for user {}: {}", userId, e.getMessage());
            return List.of();
        }
    }
}
