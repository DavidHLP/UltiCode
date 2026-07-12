package com.ulticode.modules.vote.port.adapter;

import com.ulticode.modules.solution.port.SolutionVoteReadPort;
import com.ulticode.modules.vote.entity.enums.EdgeOperationTargetType;
import com.ulticode.modules.vote.entity.enums.EdgeOperationType;
import com.ulticode.modules.vote.mapper.EdgeOperationMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
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
 * <p>The port is solution-scoped, so the {@code target_type} filter is
 * fixed here to {@link EdgeOperationTargetType#SOLUTION} rather than
 * threaded through the seam.
 *
 * @author ulticode
 */
@Component
public class SolutionVoteReadAdapter implements SolutionVoteReadPort {

    /** Solutions are the only target this port reads votes for. */
    private static final String SOLUTION_TARGET = EdgeOperationTargetType.SOLUTION.getValue();

    private final EdgeOperationMapper edgeOperationMapper;

    public SolutionVoteReadAdapter(EdgeOperationMapper edgeOperationMapper) {
        this.edgeOperationMapper = edgeOperationMapper;
    }

    @Override
    public long countLikes(String solutionId) {
        return edgeOperationMapper.countByTargetAndOperation(
                solutionId, SOLUTION_TARGET, EdgeOperationType.VOTE_UP.getValue());
    }

    @Override
    public long countDislikes(String solutionId) {
        return edgeOperationMapper.countByTargetAndOperation(
                solutionId, SOLUTION_TARGET, EdgeOperationType.VOTE_DOWN.getValue());
    }

    @Override
    public Map<String, Long> countLikesByTargets(Collection<String> solutionIds) {
        if (solutionIds == null || solutionIds.isEmpty()) return Collections.emptyMap();
        return aggregateCounts(edgeOperationMapper.countByTargetsAndOperation(
                toList(solutionIds), SOLUTION_TARGET, EdgeOperationType.VOTE_UP.getValue()));
    }

    @Override
    public Map<String, Long> countDislikesByTargets(Collection<String> solutionIds) {
        if (solutionIds == null || solutionIds.isEmpty()) return Collections.emptyMap();
        return aggregateCounts(edgeOperationMapper.countByTargetsAndOperation(
                toList(solutionIds), SOLUTION_TARGET, EdgeOperationType.VOTE_DOWN.getValue()));
    }

    @Override
    public Map<String, Integer> viewerVotes(String viewerId, Collection<String> solutionIds) {
        if (viewerId == null || solutionIds == null || solutionIds.isEmpty()) return Collections.emptyMap();
        return edgeOperationMapper.findByOperatorAndTargets(viewerId, toList(solutionIds), SOLUTION_TARGET).stream()
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

    private static List<String> toList(Collection<String> c) {
        return c instanceof List<String> ? (List<String>) c : new ArrayList<>(c);
    }

    private static Map<String, Long> aggregateCounts(List<Map<String, Object>> rows) {
        return rows.stream().collect(Collectors.toMap(
                m -> (String) m.get("target_id"),
                m -> ((Number) m.get("cnt")).longValue(),
                (a, b) -> a));
    }
}
