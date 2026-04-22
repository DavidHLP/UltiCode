package com.ulticode.modules.follow.service.impl;

import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.response.PageResult;
import com.ulticode.modules.achievement.service.AchievementTriggerService;
import com.ulticode.modules.follow.dto.FollowStatsDTO;
import com.ulticode.modules.follow.dto.UserSummaryDTO;
import com.ulticode.modules.follow.entity.UserFollow;
import com.ulticode.modules.follow.mapper.FollowMapper;
import com.ulticode.modules.follow.mapper.FollowMapper.FollowCountDTO;
import com.ulticode.modules.follow.service.FollowService;
import com.ulticode.modules.notification.service.NotificationService;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Implementation of FollowService.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FollowServiceImpl implements FollowService {

    private final FollowMapper followMapper;
    private final UserMapper userMapper;
    private final AchievementTriggerService achievementTriggerService;
    private final NotificationService notificationService;

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

            // D-10: Only notify on first follow (idempotent insert)
            User currentUser = userMapper.selectById(currentUserId);
            try {
                notificationService.createNotification(
                    targetUserId,
                    "FOLLOW",
                    "COMMUNICATION",
                    currentUser.getUsername() + " followed you",
                    "",
                    "/profile/" + currentUser.getUsername(),
                    null
                );
                log.debug("Created follow notification for user {}", targetUserId);
            } catch (Exception e) {
                log.warn("Failed to create follow notification for user {}: {}", targetUserId, e.getMessage());
            }
        }

        FollowStatsDTO stats = getFollowStats(targetUserId);

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

        followMapper.deleteRelation(currentUserId, targetUserId);
        log.info("User {} unfollowed user {}", currentUserId, targetUserId);

        FollowStatsDTO stats = getFollowStats(targetUserId);

        triggerFollowerAchievement(currentUserId, stats.getFollowingCount());
        triggerFollowerAchievement(targetUserId, stats.getFollowerCount());

        return stats;
    }

    @Override
    public PageResult<UserSummaryDTO> getFollowers(String userId, int page, int pageSize) {
        int currentPage = Math.max(1, page);
        int currentPageSize = Math.min(Math.max(1, pageSize), 100);
        long offset = (long) (currentPage - 1) * currentPageSize;

        List<UserFollow> follows = followMapper.selectByFollowingIdPaged(userId, offset, currentPageSize);
        long total = followMapper.countByFollowingId(userId);

        if (follows.isEmpty()) {
            return PageResult.of(List.of(), total, currentPage, currentPageSize);
        }

        List<String> userIds = follows.stream().map(UserFollow::getFollowerId).toList();
        Map<String, User> userMap = userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        // Batch fetch follower/following counts for all users in 2 queries total
        Map<String, FollowCountDTO> countMap = new HashMap<>();
        if (!userIds.isEmpty()) {
            List<FollowCountDTO> followerCounts = followMapper.batchFollowCounts(userIds);
            List<FollowCountDTO> followingCounts = followMapper.batchFollowingCounts(userIds);
            for (FollowCountDTO fc : followerCounts) {
                countMap.put(fc.userId(), new FollowCountDTO(fc.userId(), fc.followerCount(), 0));
            }
            for (FollowCountDTO fc : followingCounts) {
                FollowCountDTO existing = countMap.get(fc.userId());
                if (existing != null) {
                    countMap.put(fc.userId(), new FollowCountDTO(fc.userId(), existing.followerCount(), fc.followingCount()));
                } else {
                    countMap.put(fc.userId(), new FollowCountDTO(fc.userId(), 0, fc.followingCount()));
                }
            }
        }

        List<UserSummaryDTO> summaries = follows.stream()
                .map(f -> toUserSummary(userMap.get(f.getFollowerId()), countMap))
                .toList();

        return PageResult.of(summaries, total, currentPage, currentPageSize);
    }

    @Override
    public PageResult<UserSummaryDTO> getFollowing(String userId, int page, int pageSize) {
        int currentPage = Math.max(1, page);
        int currentPageSize = Math.min(Math.max(1, pageSize), 100);
        long offset = (long) (currentPage - 1) * currentPageSize;

        List<UserFollow> follows = followMapper.selectByFollowerIdPaged(userId, offset, currentPageSize);
        long total = followMapper.countByFollowerId(userId);

        if (follows.isEmpty()) {
            return PageResult.of(List.of(), total, currentPage, currentPageSize);
        }

        List<String> userIds = follows.stream().map(UserFollow::getFollowingId).toList();
        Map<String, User> userMap = userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        // Batch fetch follower/following counts for all users in 2 queries total
        Map<String, FollowCountDTO> countMap = new HashMap<>();
        if (!userIds.isEmpty()) {
            List<FollowCountDTO> followerCounts = followMapper.batchFollowCounts(userIds);
            List<FollowCountDTO> followingCounts = followMapper.batchFollowingCounts(userIds);
            for (FollowCountDTO fc : followerCounts) {
                countMap.put(fc.userId(), new FollowCountDTO(fc.userId(), fc.followerCount(), 0));
            }
            for (FollowCountDTO fc : followingCounts) {
                FollowCountDTO existing = countMap.get(fc.userId());
                if (existing != null) {
                    countMap.put(fc.userId(), new FollowCountDTO(fc.userId(), existing.followerCount(), fc.followingCount()));
                } else {
                    countMap.put(fc.userId(), new FollowCountDTO(fc.userId(), 0, fc.followingCount()));
                }
            }
        }

        List<UserSummaryDTO> summaries = follows.stream()
                .map(f -> toUserSummary(userMap.get(f.getFollowingId()), countMap))
                .toList();

        return PageResult.of(summaries, total, currentPage, currentPageSize);
    }

    @Override
    public FollowStatsDTO getFollowStats(String userId) {
        FollowStatsDTO stats = new FollowStatsDTO();
        stats.setFollowerCount(followMapper.countByFollowingId(userId));
        stats.setFollowingCount(followMapper.countByFollowerId(userId));
        return stats;
    }

    private UserSummaryDTO toUserSummary(User user, Map<String, FollowCountDTO> countMap) {
        if (user == null) {
            return null;
        }
        UserSummaryDTO dto = new UserSummaryDTO();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setAvatar(user.getAvatar());
        String bio = user.getBio();
        dto.setBio(bio != null && bio.length() > 100 ? bio.substring(0, 100) : bio);

        FollowCountDTO counts = countMap.get(user.getId());
        dto.setFollowerCount(counts != null ? counts.followerCount() : 0);
        dto.setFollowingCount(counts != null ? counts.followingCount() : 0);
        return dto;
    }

    @Override
    public boolean isFollowing(String currentUserId, String targetUserId) {
        return followMapper.exists(currentUserId, targetUserId);
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
