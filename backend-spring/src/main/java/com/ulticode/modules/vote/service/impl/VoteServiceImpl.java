package com.ulticode.modules.vote.service.impl;

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
 * Implementation of VoteService.
 * Handles three-state voting logic: upvote, downvote, and neutral.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VoteServiceImpl implements VoteService {

    private final EdgeOperationMapper edgeOperationMapper;

    @Override
    @Transactional
    public VoteResultVO vote(String userId, VoteDTO dto) {
        String targetId = dto.getTargetId();
        EdgeOperationTargetType targetType = dto.getTargetType();
        Integer value = dto.getValue();

        log.debug("User {} voting on {}:{} with value {}", userId, targetType, targetId, value);

        // Validate vote value
        if (value == null || (value != 1 && value != -1 && value != 0)) {
            throw new IllegalArgumentException("Vote value must be 1 (upvote), -1 (downvote), or 0 (neutral)");
        }

        // Check existing votes
        boolean hasUpvote = edgeOperationMapper.existsByOperatorAndTarget(
                userId, targetId, targetType.getValue(), EdgeOperationType.VOTE_UP.getValue()) > 0;
        boolean hasDownvote = edgeOperationMapper.existsByOperatorAndTarget(
                userId, targetId, targetType.getValue(), EdgeOperationType.VOTE_DOWN.getValue()) > 0;

        if (value == 1) {
            // Upvote
            if (hasUpvote) {
                // Toggle off: remove upvote
                removeVote(userId, targetId, targetType, EdgeOperationType.VOTE_UP);
                log.debug("Removed upvote for {}:{} by user {}", targetType, targetId, userId);
            } else {
                // Add upvote, remove downvote if exists
                if (hasDownvote) {
                    removeVote(userId, targetId, targetType, EdgeOperationType.VOTE_DOWN);
                }
                addVote(userId, targetId, targetType, EdgeOperationType.VOTE_UP);
                log.debug("Added upvote for {}:{} by user {}", targetType, targetId, userId);
            }
        } else if (value == -1) {
            // Downvote
            if (hasDownvote) {
                // Toggle off: remove downvote
                removeVote(userId, targetId, targetType, EdgeOperationType.VOTE_DOWN);
                log.debug("Removed downvote for {}:{} by user {}", targetType, targetId, userId);
            } else {
                // Add downvote, remove upvote if exists
                if (hasUpvote) {
                    removeVote(userId, targetId, targetType, EdgeOperationType.VOTE_UP);
                }
                addVote(userId, targetId, targetType, EdgeOperationType.VOTE_DOWN);
                log.debug("Added downvote for {}:{} by user {}", targetType, targetId, userId);
            }
        } else {
            // value == 0: remove all votes (neutral)
            if (hasUpvote) {
                removeVote(userId, targetId, targetType, EdgeOperationType.VOTE_UP);
            }
            if (hasDownvote) {
                removeVote(userId, targetId, targetType, EdgeOperationType.VOTE_DOWN);
            }
            log.debug("Removed all votes for {}:{} by user {}", targetType, targetId, userId);
        }

        return getVoteStatus(userId, targetId, targetType);
    }

    @Override
    public VoteResultVO getVoteStatus(String userId, String targetId, EdgeOperationTargetType targetType) {
        long likes = edgeOperationMapper.countByTargetAndOperation(
                targetId, targetType.getValue(), EdgeOperationType.VOTE_UP.getValue());
        long dislikes = edgeOperationMapper.countByTargetAndOperation(
                targetId, targetType.getValue(), EdgeOperationType.VOTE_DOWN.getValue());

        int userVote = 0;
        if (userId != null) {
            boolean hasUpvote = edgeOperationMapper.existsByOperatorAndTarget(
                    userId, targetId, targetType.getValue(), EdgeOperationType.VOTE_UP.getValue()) > 0;
            boolean hasDownvote = edgeOperationMapper.existsByOperatorAndTarget(
                    userId, targetId, targetType.getValue(), EdgeOperationType.VOTE_DOWN.getValue()) > 0;

            if (hasUpvote) {
                userVote = 1;
            } else if (hasDownvote) {
                userVote = -1;
            }
        }

        return new VoteResultVO(targetId, targetType.getValue(), likes, dislikes, userVote);
    }

    // ==================== Private Helper Methods ====================

    private void addVote(String userId, String targetId, EdgeOperationTargetType targetType, EdgeOperationType operationType) {
        EdgeOperation operation = new EdgeOperation();
        operation.setTargetId(targetId);
        operation.setTargetType(targetType);
        operation.setOperatorId(userId);
        operation.setOperationType(operationType);
        edgeOperationMapper.insert(operation);
    }

    private void removeVote(String userId, String targetId, EdgeOperationTargetType targetType, EdgeOperationType operationType) {
        edgeOperationMapper.deleteByOperatorAndTarget(userId, targetId, targetType.getValue(), operationType.getValue());
    }
}
