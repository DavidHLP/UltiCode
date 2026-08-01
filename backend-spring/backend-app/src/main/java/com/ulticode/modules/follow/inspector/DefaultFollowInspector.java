package com.ulticode.modules.follow.inspector;

import com.ulticode.common.error.BaseErrorCode;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.response.PageResult;
import com.ulticode.common.response.PaginationRequest;
import com.ulticode.app.error.FollowErrorCode;
import com.ulticode.modules.follow.dto.FollowStatsDTO;
import com.ulticode.modules.follow.dto.UserSummaryDTO;
import com.ulticode.modules.follow.entity.UserFollow;
import com.ulticode.modules.follow.mapper.FollowMapper;
import com.ulticode.modules.follow.mapper.FollowMapper.FollowCountDTO;
import com.ulticode.modules.follow.port.UserReadPort;
import com.ulticode.modules.follow.port.UserReadPort.UserSummaryData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Default adapter for {@link FollowInspector}. Side-effect free.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultFollowInspector implements FollowInspector {

    private final FollowMapper followMapper;
    private final UserReadPort userReadPort;

    @Override
    public PageResult<UserSummaryDTO> getFollowers(String userId, int page, int pageSize) {
        PaginationRequest pageRequest = PaginationRequest.of(page, pageSize);
        int currentPage = pageRequest.page();
        int currentPageSize = pageRequest.pageSize();
        long offset = (long) (currentPage - 1) * currentPageSize;

        List<UserFollow> follows = followMapper.selectByFollowingIdPaged(userId, offset, currentPageSize);
        long total = followMapper.countByFollowingId(userId);

        if (follows.isEmpty()) {
            return PageResult.of(List.of(), total, currentPage, currentPageSize);
        }

        List<String> userIds = follows.stream().map(UserFollow::getFollowerId).toList();
        Map<String, UserSummaryData> userMap = userReadPort.findByIds(userIds);

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
        PaginationRequest pageRequest = PaginationRequest.of(page, pageSize);
        int currentPage = pageRequest.page();
        int currentPageSize = pageRequest.pageSize();
        long offset = (long) (currentPage - 1) * currentPageSize;

        List<UserFollow> follows = followMapper.selectByFollowerIdPaged(userId, offset, currentPageSize);
        long total = followMapper.countByFollowerId(userId);

        if (follows.isEmpty()) {
            return PageResult.of(List.of(), total, currentPage, currentPageSize);
        }

        List<String> userIds = follows.stream().map(UserFollow::getFollowingId).toList();
        Map<String, UserSummaryData> userMap = userReadPort.findByIds(userIds);

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

    @Override
    public boolean isFollowing(String currentUserId, String targetUserId) {
        if (currentUserId.equals(targetUserId)) {
            throw new BusinessException(BaseErrorCode.FORBIDDEN, "Cannot query follow status of yourself");
        }
        if (!userReadPort.exists(targetUserId)) {
            throw new BusinessException(BaseErrorCode.NOT_FOUND, "User not found");
        }
        return followMapper.exists(currentUserId, targetUserId);
    }

    private UserSummaryDTO toUserSummary(UserSummaryData user, Map<String, FollowCountDTO> countMap) {
        if (user == null) {
            return null;
        }
        UserSummaryDTO dto = new UserSummaryDTO();
        dto.setId(user.id());
        dto.setUsername(user.username());
        dto.setAvatar(user.avatar());
        String bio = user.bio();
        dto.setBio(bio != null && bio.length() > 100 ? bio.substring(0, 100) : bio);

        FollowCountDTO counts = countMap.get(user.id());
        dto.setFollowerCount(counts != null ? counts.followerCount() : 0);
        dto.setFollowingCount(counts != null ? counts.followingCount() : 0);
        return dto;
    }
}
