package com.ulticode.modules.vote.port.adapter;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ulticode.modules.solution.port.SolutionVoteReadPort;
import com.ulticode.modules.vote.entity.EdgeOperation;
import com.ulticode.modules.vote.entity.enums.EdgeOperationType;
import com.ulticode.modules.vote.mapper.EdgeOperationMapper;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Default adapter that satisfies {@link SolutionVoteReadPort} from
 * {@code EdgeOperationMapper}. Lives in the {@code vote} module so the
 * {@code solution} module never imports the mapper.
 *
 * @author ulticode
 */
@Component
public class SolutionVoteReadAdapter implements SolutionVoteReadPort {

    private final EdgeOperationMapper edgeOperationMapper;

    public SolutionVoteReadAdapter(EdgeOperationMapper edgeOperationMapper) {
        this.edgeOperationMapper = edgeOperationMapper;
    }

    @Override
    public long countLikes(String targetId, String targetType) {
        return edgeOperationMapper.countByTargetAndOperation(
                targetId, targetType, EdgeOperationType.VOTE_UP.getValue());
    }

    @Override
    public long countDislikes(String targetId, String targetType) {
        return edgeOperationMapper.countByTargetAndOperation(
                targetId, targetType, EdgeOperationType.VOTE_DOWN.getValue());
    }

    @Override
    public Map<String, Long> countLikesByTargets(Collection<String> targetIds, String targetType) {
        if (targetIds == null || targetIds.isEmpty()) return Collections.emptyMap();
        return aggregateCounts(edgeOperationMapper.countByTargetsAndOperation(
                toList(targetIds), targetType, EdgeOperationType.VOTE_UP.getValue()));
    }

    @Override
    public Map<String, Long> countDislikesByTargets(Collection<String> targetIds, String targetType) {
        if (targetIds == null || targetIds.isEmpty()) return Collections.emptyMap();
        return aggregateCounts(edgeOperationMapper.countByTargetsAndOperation(
                toList(targetIds), targetType, EdgeOperationType.VOTE_DOWN.getValue()));
    }

    @Override
    public Map<String, Integer> viewerVotes(String viewerId, Collection<String> targetIds, String targetType) {
        if (viewerId == null || targetIds == null || targetIds.isEmpty()) return Collections.emptyMap();
        return edgeOperationMapper.findByOperatorAndTargets(viewerId, toList(targetIds), targetType).stream()
                .collect(Collectors.toMap(
                        m -> (String) m.get("target_id"),
                        m -> {
                            String opType = (String) m.get("operation_type");
                            if (EdgeOperationType.VOTE_UP.getValue().equals(opType)) return 1;
                            if (EdgeOperationType.VOTE_DOWN.getValue().equals(opType)) return -1;
                            return 0;
                        },
                        (a, b) -> a));
    }

    @Override
    public List<Map<String, Object>> findRawByOperatorAndTargets(
            String operatorId, Collection<String> targetIds, String targetType) {
        if (operatorId == null || targetIds == null || targetIds.isEmpty()) return Collections.emptyList();
        return edgeOperationMapper.findByOperatorAndTargets(operatorId, toList(targetIds), targetType);
    }

    private static List<String> toList(Collection<String> c) {
        return c instanceof List ? (List<String>) c : new java.util.ArrayList<>(c);
    }

    /**
     * Read a single user's vote on one target via the mapper's
     * {@code selectOne} entry point. Exposed for callers that need only
     * one target (kept here so the port stays focused on the
     * projection's hot path).
     */
    public EdgeOperation selectOneVote(String userId, String targetId, String targetType) {
        LambdaQueryWrapper<EdgeOperation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(EdgeOperation::getOperatorId, userId)
                .eq(EdgeOperation::getTargetId, targetId)
                .eq(EdgeOperation::getTargetType, targetType);
        return edgeOperationMapper.selectOne(wrapper);
    }

    private static Map<String, Long> aggregateCounts(List<Map<String, Object>> rows) {
        return rows.stream().collect(Collectors.toMap(
                m -> (String) m.get("target_id"),
                m -> ((Number) m.get("cnt")).longValue(),
                (a, b) -> a));
    }
}