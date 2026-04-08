package com.ulticode.modules.edgeoperations.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ulticode.modules.bookmark.entity.Bookmark;
import com.ulticode.modules.bookmark.mapper.BookmarkMapper;
import com.ulticode.modules.edgeoperations.dto.EdgeOperationDTO;
import com.ulticode.modules.edgeoperations.dto.EdgeOperationResponseVO;
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
 * Implementation of EdgeOperationsService.
 * Handles edge operations like voting, analyzing, viewing content.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EdgeOperationsServiceImpl implements EdgeOperationsService {

    private final VoteService voteService;
    private final EdgeOperationMapper edgeOperationMapper;
    private final BookmarkMapper bookmarkMapper;
    private final SolutionMapper solutionMapper;

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

        // Return interaction stats
        return getInteractions(userId, targetId, targetType);
    }

    @Override
    public EdgeOperationResponseVO getInteractions(String userId, String targetId, EdgeOperationTargetType targetType) {
        // Get vote status from VoteService
        VoteResultVO voteStatus = voteService.getVoteStatus(userId, targetId, targetType);

        // Get favorites count
        long favorites = getFavoritesCount(targetId, targetType);

        // Build response
        return EdgeOperationResponseVO.builder()
                .likes(voteStatus.getLikes())
                .dislikes(voteStatus.getDislikes())
                .favorites(favorites)
                .viewer(EdgeOperationResponseVO.ViewerState.builder()
                        .vote(voteStatus.getUserVote())
                        .build())
                .build();
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

        // Get favorites count
        long favorites = getFavoritesCount(targetId, targetType);

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
     * Toggle an operation: create if not exists, delete if exists.
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
     * Get the count of users who favorited/bookmarked a target.
     * For PROBLEM target type: count unique users who bookmarked or added to problem list.
     * For other types: return 0 (can be extended later).
     */
    private long getFavoritesCount(String targetId, EdgeOperationTargetType targetType) {
        if (targetType == EdgeOperationTargetType.PROBLEM) {
            // Count total bookmarks for this problem
            // Note: This counts all bookmarks, not unique users
            // For unique user count, a custom query would be needed in BookmarkMapper
            QueryWrapper<Bookmark> wrapper = new QueryWrapper<>();
            wrapper.eq("target_id", targetId)
                   .eq("target_type", "PROBLEM");
            return bookmarkMapper.selectCount(wrapper);
        }
        // For other target types, return 0 (can be extended later)
        return 0;
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
