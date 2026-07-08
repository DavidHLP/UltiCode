package com.ulticode.modules.follow.service.impl;

import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.modules.achievement.service.AchievementTriggerService;
import com.ulticode.modules.follow.dto.FollowStatsDTO;
import com.ulticode.modules.follow.inspector.FollowInspector;
import com.ulticode.modules.follow.mapper.FollowMapper;
import com.ulticode.modules.follow.service.FollowService;
import com.ulticode.modules.notification.service.NotificationDispatchService;
import com.ulticode.modules.notification.service.NotificationService;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Write-path implementation of {@link FollowService}.
 *
 * <p>Owns the two mutations (follow / unfollow): idempotent insert /
 * delete against {@code user_follow}, follow-notification dispatch, and
 * the post-mutation follower-count achievement trigger. Every read —
 * including the post-mutation stats this service returns — is delegated
 * to {@link FollowInspector} so the pagination, batch-count enrichment,
 * and count-query logic have a single owner instead of leaking across
 * the write path's bean graph.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FollowServiceImpl implements FollowService {

    private final FollowMapper followMapper;
    private final UserMapper userMapper;
    private final AchievementTriggerService achievementTriggerService;
    private final NotificationService notificationService;
    private final NotificationDispatchService notificationDispatchService;
    /**
     * ADR-004 M4c: typed intent dispatcher. Active when
     * {@code app.features.use-notification-intent=true}.
     */
    private final com.ulticode.modules.notification.dispatcher.NotificationDispatcher notificationDispatcher;
    private final com.ulticode.modules.submission.config.FeatureFlagsProperties featureFlags;
    /**
     * Read-side deep module. Injected so the post-mutation stats return
     * value can be served without re-implementing the count read here.
     */
    private final FollowInspector followInspector;

    @Override
    public FollowStatsDTO follow(String currentUserId, String targetUserId) {
        if (currentUserId.equals(targetUserId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Cannot follow yourself");
        }

        User target = userMapper.selectById(targetUserId);
        if (target == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        if (!followMapper.exists(currentUserId, targetUserId)) {
            followMapper.insertIdempotent(currentUserId, targetUserId);
            log.info("User {} followed user {}", currentUserId, targetUserId);

            // D-10: Only notify on first follow (idempotent insert).
            // Q20: respect COMMUNICATION preference — opt-out users won't see this.
            // ADR-004 M4c: when the flag is on, dispatch a typed
            // FollowReceivedIntent (InApp + WebSocket; Email skipped per
            // channel matrix). Otherwise fall through to the legacy path.
            User currentUser = userMapper.selectById(currentUserId);
            try {
                if (featureFlags.isUseNotificationIntent()) {
                    notificationDispatcher.dispatch(
                            com.ulticode.modules.notification.intent.FollowReceivedIntent.of(
                                    currentUser, targetUserId));
                } else {
                    notificationDispatchService.dispatch(
                        targetUserId,
                        "FOLLOW",
                        "COMMUNICATION",
                        currentUser.getUsername() + " followed you",
                        "",
                        "/profile/" + currentUser.getUsername(),
                        null,
                        false
                    );
                }
                log.debug("Created follow notification for user {}", targetUserId);
            } catch (Exception e) {
                log.warn("Failed to create follow notification for user {}: {}", targetUserId, e.getMessage());
            }
        }

        FollowStatsDTO stats = followInspector.getFollowStats(targetUserId);

        triggerFollowerAchievement(currentUserId, stats.getFollowingCount());
        triggerFollowerAchievement(targetUserId, stats.getFollowerCount());

        return stats;
    }

    @Override
    public FollowStatsDTO unfollow(String currentUserId, String targetUserId) {
        if (currentUserId.equals(targetUserId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Cannot unfollow yourself");
        }

        User target = userMapper.selectById(targetUserId);
        if (target == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        if (followMapper.deleteIfExists(currentUserId, targetUserId) > 0) {
            log.info("User {} unfollowed user {}", currentUserId, targetUserId);
        } else {
            log.debug("User {} already not following {}, skip", currentUserId, targetUserId);
        }

        FollowStatsDTO stats = followInspector.getFollowStats(targetUserId);

        triggerFollowerAchievement(currentUserId, stats.getFollowingCount());
        triggerFollowerAchievement(targetUserId, stats.getFollowerCount());

        return stats;
    }

    @Async
    public void triggerFollowerAchievement(String userId, int count) {
        try {
            achievementTriggerService.onFollowCountUpdated(userId, count);
        } catch (Exception e) {
            log.warn("Failed to trigger follow achievement for user {}: {}", userId, e.getMessage());
        }
    }
}
