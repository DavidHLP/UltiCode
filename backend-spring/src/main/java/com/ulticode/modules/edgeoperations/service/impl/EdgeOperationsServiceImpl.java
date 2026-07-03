package com.ulticode.modules.edgeoperations.service.impl;

import com.ulticode.modules.edgeoperations.dto.EdgeOperationDTO;
import com.ulticode.modules.edgeoperations.dto.EdgeOperationResponseVO;
import com.ulticode.modules.edgeoperations.inspector.EdgeOperationInspector;
import com.ulticode.modules.edgeoperations.service.EdgeOperationsService;
import com.ulticode.modules.solution.entity.Solution;
import com.ulticode.modules.solution.mapper.SolutionMapper;
import com.ulticode.modules.vote.dto.VoteDTO;
import com.ulticode.modules.vote.dto.VoteResultVO;
import com.ulticode.modules.vote.entity.EdgeOperation;
import com.ulticode.modules.vote.entity.enums.EdgeOperationTargetType;
import com.ulticode.modules.vote.entity.enums.EdgeOperationType;
import com.ulticode.modules.vote.mapper.EdgeOperationMapper;
import com.ulticode.modules.vote.service.VoteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of {@link EdgeOperationsService}. Owns the write
 * path of edge operations: voting, analyzing, viewing, favoriting,
 * and the denormalized vote-count projection onto the
 * {@code solution} table.
 *
 * <p>Pure-read concerns (aggregated interaction stats, favorites
 * count) have been extracted into
 * {@link EdgeOperationInspector}; this class injects the inspector
 * so {@code performOperation} can return the same response shape as
 * the read endpoints after a mutation. The {@code BookmarkMapper} is
 * no longer wired into the service — the inspector is the single
 * collaborator that knows how to count favorites.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EdgeOperationsServiceImpl implements EdgeOperationsService {

    private final VoteService voteService;
    private final EdgeOperationMapper edgeOperationMapper;
    private final SolutionMapper solutionMapper;
    private final EdgeOperationInspector edgeOperationInspector;

    @Override
    @Transactional
    public EdgeOperationResponseVO performOperation(String userId, EdgeOperationDTO dto) {
        String targetId = dto.getTargetId();
        EdgeOperationTargetType targetType = dto.getTargetType();
        EdgeOperationType operationType = dto.getOperationType();

        log.debug("User {} performing {} on {}:{}", userId, operationType, targetType, targetId);

        // Handle vote operations by delegating to VoteService
        if (operationType == EdgeOperationType.VOTE_UP) {
            return handleVoteOperation(userId, targetId, targetType, 1);
        } else if (operationType == EdgeOperationType.VOTE_DOWN) {
            return handleVoteOperation(userId, targetId, targetType, -1);
        }

        // For other operations (ANALYZE, VIEW, etc.), use toggle logic
        toggleOperation(userId, targetId, targetType, operationType);

        // Return interaction stats via the read seam so the response shape
        // matches the GET /interactions endpoint.
        return edgeOperationInspector.getInteractions(userId, targetId, targetType);
    }

    // ==================== Private Helper Methods ====================

    /**
     * Handle vote operations by delegating to VoteService.
     */
    private EdgeOperationResponseVO handleVoteOperation(String userId, String targetId,
                                                         EdgeOperationTargetType targetType, int voteValue) {
        VoteDTO voteDTO = new VoteDTO();
        voteDTO.setTargetId(targetId);
        voteDTO.setTargetType(targetType);
        voteDTO.setValue(voteValue);

        VoteResultVO voteResult = voteService.vote(userId, voteDTO);

        // Update denormalized vote counts on solution entity
        updateSolutionVoteCounts(targetId, targetType);

        // Read favorites count via the inspector seam (BookmarkMapper
        // lives only on EdgeOperationInspector now).
        long favorites = edgeOperationInspector.getFavoritesCount(targetId, targetType);

        return EdgeOperationResponseVO.builder()
                .likes(voteResult.getLikes())
                .dislikes(voteResult.getDislikes())
                .favorites(favorites)
                .viewer(EdgeOperationResponseVO.ViewerState.builder()
                        .vote(voteResult.getUserVote())
                        .build())
                .build();
    }

    /**
     * Toggle an edge operation: insert if absent, delete if present.
     *
     * <p>Used for non-vote operations (ANALYZE, VIEW, LIKE, DISLIKE,
     * FAVORITE). The toggle is "silent" — the response VO only exposes
     * aggregated vote counts ({@code likes}, {@code dislikes}) and
     * bookmark count ({@code favorites}); it does NOT expose a per-user
     * flag for whether this user has, e.g., liked via FAVORITE. Use
     * {@code voteService.getVoteStatus} for vote operations if you need
     * per-user state. See
     * docs/edge-operations-api-test-report-2026-06-11.md §六.
     */
    private void toggleOperation(String userId, String targetId,
                                  EdgeOperationTargetType targetType, EdgeOperationType operationType) {
        boolean exists = edgeOperationMapper.existsByOperatorAndTarget(
                userId, targetId, targetType.getValue(), operationType.getValue()) > 0;

        if (exists) {
            // Toggle off: remove the operation
            edgeOperationMapper.deleteByOperatorAndTarget(
                    userId, targetId, targetType.getValue(), operationType.getValue());
            log.debug("Removed {} for {}:{} by user {}", operationType, targetType, targetId, userId);
        } else {
            // Toggle on: add the operation
            EdgeOperation operation = new EdgeOperation();
            operation.setTargetId(targetId);
            operation.setTargetType(targetType);
            operation.setOperatorId(userId);
            operation.setOperationType(operationType);
            edgeOperationMapper.insert(operation);
            log.debug("Added {} for {}:{} by user {}", operationType, targetType, targetId, userId);
        }
    }

    /**
     * Update denormalized vote counts on solution entity.
     * Called after vote operations on SOLUTION target type.
     */
    private void updateSolutionVoteCounts(String solutionId, EdgeOperationTargetType targetType) {
        if (targetType != EdgeOperationTargetType.SOLUTION) {
            return;
        }

        Solution solution = solutionMapper.selectById(solutionId);
        if (solution == null) {
            log.warn("Solution not found for vote count update: {}", solutionId);
            return;
        }

        // Count likes and dislikes from edge_operations
        long likes = edgeOperationMapper.countByTargetAndOperation(
                solutionId, EdgeOperationTargetType.SOLUTION.getValue(), EdgeOperationType.VOTE_UP.getValue());
        long dislikes = edgeOperationMapper.countByTargetAndOperation(
                solutionId, EdgeOperationTargetType.SOLUTION.getValue(), EdgeOperationType.VOTE_DOWN.getValue());

        solution.setLikes((int) likes);
        solution.setDislikes((int) dislikes);
        solutionMapper.updateById(solution);

        log.debug("Updated solution {} vote counts: likes={}, dislikes={}", solutionId, likes, dislikes);
    }
}
