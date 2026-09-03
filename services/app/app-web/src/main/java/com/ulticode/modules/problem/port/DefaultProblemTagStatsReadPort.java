package com.ulticode.modules.problem.port;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ulticode.modules.problem.entity.ProblemTag;
import com.ulticode.modules.problem.entity.ProblemTagRelation;
import com.ulticode.modules.problem.mapper.ProblemTagMapper;
import com.ulticode.modules.problem.mapper.ProblemTagRelationMapper;
import com.ulticode.submission.api.service.SubmissionUserStatsPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Production adapter for {@link ProblemTagStatsReadPort}.
 * Joins App-owned tag relations with accepted problem ids returned by the
 * Submission owner. The App never queries the Submission table directly.
 *
 * <p>Non-throwing: returns an empty list when data access fails.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultProblemTagStatsReadPort implements ProblemTagStatsReadPort {

    private final ProblemTagRelationMapper problemTagRelationMapper;
    private final ProblemTagMapper problemTagMapper;
    private final SubmissionUserStatsPort submissionUserStats;

    @Override
    public List<Map<String, Object>> findTagStatsByUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            return List.of();
        }
        try {
            List<Long> acceptedProblemIds = submissionUserStats.findAcceptedProblemIdsByUserId(userId);
            if (acceptedProblemIds == null || acceptedProblemIds.isEmpty()) {
                return List.of();
            }
            Set<Long> accepted = new LinkedHashSet<>(acceptedProblemIds);
            List<ProblemTagRelation> relations = problemTagRelationMapper.selectList(
                    new LambdaQueryWrapper<ProblemTagRelation>()
                            .in(ProblemTagRelation::getProblemId, accepted));
            if (relations == null || relations.isEmpty()) {
                return List.of();
            }

            Map<String, Set<Long>> problemIdsByTag = relations.stream()
                    .filter(relation -> relation.getTagId() != null
                            && relation.getProblemId() != null
                            && accepted.contains(relation.getProblemId()))
                    .collect(Collectors.groupingBy(
                            ProblemTagRelation::getTagId,
                            LinkedHashMap::new,
                            Collectors.mapping(ProblemTagRelation::getProblemId,
                                    Collectors.toCollection(LinkedHashSet::new))));
            if (problemIdsByTag.isEmpty()) {
                return List.of();
            }
            Map<String, ProblemTag> tags = problemTagMapper.selectBatchIds(problemIdsByTag.keySet())
                    .stream()
                    .collect(Collectors.toMap(ProblemTag::getId, tag -> tag,
                            (left, right) -> left, LinkedHashMap::new));
            return problemIdsByTag.entrySet().stream()
                    .map(entry -> {
                        ProblemTag tag = tags.get(entry.getKey());
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("tagName", tag == null ? null : tag.getLabel());
                        row.put("tagSlug", tag == null ? null : tag.getSlug());
                        row.put("count", (long) entry.getValue().size());
                        return row;
                    })
                    .sorted(Comparator.<Map<String, Object>>comparingLong(
                                    row -> -((Number) row.get("count")).longValue())
                            .thenComparing(row -> String.valueOf(row.get("tagSlug"))))
                    .toList();
        } catch (Exception e) {
            log.warn("Failed to read tag stats for user {}: {}", userId, e.getMessage());
            return List.of();
        }
    }
}
