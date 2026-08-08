package com.ulticode.modules.follow.service.impl;

import com.ulticode.app.api.event.FollowEventPublisher;
import com.ulticode.app.error.FollowErrorCode;
import com.ulticode.common.error.BaseErrorCode;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.modules.follow.dto.FollowStatsDTO;
import com.ulticode.modules.follow.inspector.FollowInspector;
import com.ulticode.modules.follow.mapper.FollowMapper;
import com.ulticode.modules.follow.port.UserReadPort;
import com.ulticode.modules.follow.port.UserReadPort.UserSummaryData;
import com.ulticode.modules.follow.service.FollowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Write-path implementation of {@link FollowService}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FollowServiceImpl implements FollowService {

    private final FollowMapper followMapper;
    private final UserReadPort userReadPort;
    private final FollowEventPublisher followEventPublisher;
    private final FollowInspector followInspector;

    @Override
    public FollowStatsDTO follow(String currentUserId, String targetUserId) {
        if (currentUserId.equals(targetUserId)) {
            throw new BusinessException(FollowErrorCode.CANNOT_FOLLOW_SELF);
        }

        UserSummaryData target = userReadPort.findById(targetUserId);
        if (target == null) {
            throw new BusinessException(BaseErrorCode.NOT_FOUND, "Target user not found");
        }

        if (!followMapper.exists(currentUserId, targetUserId)) {
            followMapper.insertIdempotent(currentUserId, targetUserId);
            log.info("User {} followed user {}", currentUserId, targetUserId);

            UserSummaryData currentUser = userReadPort.findById(currentUserId);
            FollowStatsDTO targetStats = followInspector.getFollowStats(targetUserId);
            FollowStatsDTO followerStats = followInspector.getFollowStats(currentUserId);

            try {
                String followerUsername = currentUser != null ? currentUser.username() : currentUserId;
                followEventPublisher.publishFollowEvent(
                        currentUserId,
                        followerUsername,
                        targetUserId,
                        targetStats.getFollowerCount(),
                        followerStats.getFollowingCount()
                );
            } catch (Exception e) {
                log.warn("Failed to publish follow event for user {}: {}", targetUserId, e.getMessage());
            }
        }

        return followInspector.getFollowStats(targetUserId);
    }

    @Override
    public FollowStatsDTO unfollow(String currentUserId, String targetUserId) {
        if (currentUserId.equals(targetUserId)) {
            throw new BusinessException(BaseErrorCode.FORBIDDEN, "Cannot unfollow yourself");
        }

        UserSummaryData target = userReadPort.findById(targetUserId);
        if (target == null) {
            throw new BusinessException(BaseErrorCode.NOT_FOUND, "Target user not found");
        }

        if (followMapper.deleteIfExists(currentUserId, targetUserId) > 0) {
            log.info("User {} unfollowed user {}", currentUserId, targetUserId);

            FollowStatsDTO targetStats = followInspector.getFollowStats(targetUserId);
            FollowStatsDTO followerStats = followInspector.getFollowStats(currentUserId);

            try {
                followEventPublisher.publishUnfollowEvent(
                        currentUserId,
                        targetUserId,
                        targetStats.getFollowerCount(),
                        followerStats.getFollowingCount()
                );
            } catch (Exception e) {
                log.warn("Failed to publish unfollow event for user {}: {}", targetUserId, e.getMessage());
            }
        } else {
            log.debug("User {} already not following {}, skip", currentUserId, targetUserId);
        }

        return followInspector.getFollowStats(targetUserId);
    }
}
