package com.ulticode.modules.problem.port.adapter;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ulticode.modules.problem.entity.ProblemTag;
import com.ulticode.modules.problem.entity.ProblemTagRelation;
import com.ulticode.modules.problem.mapper.ProblemTagMapper;
import com.ulticode.modules.problem.mapper.ProblemTagRelationMapper;
import com.ulticode.modules.solution.port.ProblemTagReadPort;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Default adapter for {@link ProblemTagReadPort} backed by
 * {@code ProblemTagRelationMapper} + {@code ProblemTagMapper}. Lives in
 * the {@code problem} module so {@code solution} never imports the
 * mappers directly.
 *
 * <p>The batch lookup runs two queries regardless of batch size — one for
 * the relations, one for the tag labels — so list pages no longer pay a
 * per-row N+1 for the topic name.
 *
 * @author ulticode
 */
@Component
public class ProblemTagReadAdapter implements ProblemTagReadPort {

    private final ProblemTagRelationMapper relationMapper;
    private final ProblemTagMapper tagMapper;

    public ProblemTagReadAdapter(ProblemTagRelationMapper relationMapper,
                                 ProblemTagMapper tagMapper) {
        this.relationMapper = relationMapper;
        this.tagMapper = tagMapper;
    }

    @Override
    public Map<Long, String> findFirstTagLabels(Collection<Long> problemIds) {
        if (problemIds == null || problemIds.isEmpty()) {
            return Collections.emptyMap();
        }

        List<ProblemTagRelation> rows = relationMapper.selectList(
                new LambdaQueryWrapper<ProblemTagRelation>()
                        .select(ProblemTagRelation::getProblemId, ProblemTagRelation::getTagId)
                        .in(ProblemTagRelation::getProblemId, problemIds));
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyMap();
        }

        // First tag id per problem in encounter order — matches the previous
        // single-row findTagIdsByProblemId(...).get(0) behaviour.
        Map<Long, String> firstTagIdByProblem = new HashMap<>(rows.size());
        for (ProblemTagRelation row : rows) {
            firstTagIdByProblem.putIfAbsent(row.getProblemId(), row.getTagId());
        }
        if (firstTagIdByProblem.isEmpty()) {
            return Collections.emptyMap();
        }

        Set<String> tagIds = new HashSet<>(firstTagIdByProblem.values());
        List<ProblemTag> tags = tagMapper.selectBatchIds(tagIds);
        Map<String, String> labelByTagId = new HashMap<>(tagIds.size());
        if (tags != null) {
            for (ProblemTag tag : tags) {
                labelByTagId.put(tag.getId(), tag.getLabel());
            }
        }

        Map<Long, String> result = new HashMap<>(firstTagIdByProblem.size());
        for (Map.Entry<Long, String> entry : firstTagIdByProblem.entrySet()) {
            String label = labelByTagId.get(entry.getValue());
            if (label != null) {
                result.put(entry.getKey(), label);
            }
        }
        return result;
    }
}
