package com.ulticode.modules.problem.port;

import com.ulticode.modules.edgeoperations.inspector.EdgeOperationInspector;
import com.ulticode.modules.vote.entity.enums.EdgeOperationTargetType;
import com.ulticode.modules.vote.mapper.EdgeOperationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Default adapter for {@link ProblemInteractionQueryPort} backed by
 * {@code EdgeOperationInspector} and {@code EdgeOperationMapper}. Lives in
 * backend-legacy; when the problem module migrates to backend-app, this
 * adapter is replaced or the underlying data access moves with it.
 *
 * @author ulticode
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultProblemInteractionQueryAdapter implements ProblemInteractionQueryPort {

    private final EdgeOperationInspector edgeOperationInspector;
    private final EdgeOperationMapper edgeOperationMapper;

    @Override
    public int countFavorites(Long problemId) {
        try {
            var edgeOps = edgeOperationInspector.getInteractions(
                    null, String.valueOf(problemId), EdgeOperationTargetType.PROBLEM);
            return (int) edgeOps.getFavorites();
        } catch (Exception e) {
            log.warn("Failed to query edge-operations favorites for problem {}", problemId);
            return 0;
        }
    }

    @Override
    public String findViewerReaction(String userId, Long problemId) {
        try {
            return edgeOperationMapper.findViewerReaction(
                    userId, String.valueOf(problemId), EdgeOperationTargetType.PROBLEM.name());
        } catch (Exception e) {
            log.warn("Failed to query viewer reaction for problem {} user {}: {}",
                    problemId, userId, e.getMessage());
            return null;
        }
    }
}
