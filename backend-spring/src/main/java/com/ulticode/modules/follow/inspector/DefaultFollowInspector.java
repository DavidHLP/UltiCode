package com.ulticode.modules.follow.inspector;

import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.response.PageResult;
import com.ulticode.modules.follow.dto.FollowStatsDTO;
import com.ulticode.modules.follow.dto.UserSummaryDTO;
import com.ulticode.modules.follow.entity.UserFollow;
import com.ulticode.modules.follow.mapper.FollowMapper;
import com.ulticode.modules.follow.mapper.FollowMapper.FollowCountDTO;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Default adapter for {@link FollowInspector}. Side-effect free: reads
 * from {@link FollowMapper} and {@link UserMapper} only.
 *
 * <p>This is the new home for the follower / following / stats / status
 * reads that {@code FollowService} used to expose. The service kept its
 * write-path contract (follow / unfollow) and delegates every read to
 * this module; the HTTP caller ({@code FollowController}) and the write
 * module ({@code FollowServiceImpl#follow} / {@code #unfollow}) reuse
 * the same seam — the latter via {@link #getFollowStats(String)} — so
 * the pagination, batch-count enrichment, and {@code UserSummaryDTO}
 * formatting live in one deep module instead of leaking across the
 * service's write-path bean graph.
 *
 * <p>Neither collaborator is exclusive to the inspector: the write
 * module still holds {@code FollowMapper} (for the idempotent insert /
 * delete) and {@code UserMapper} (for the target-user existence check).
 * The concentration win is the read <em>logic</em> — the batch count
 * join, the page-clamp invariants, the bio truncation rule — which now
 * has a single owner. The deletion test passes: removing this class
 * would push those ~90 lines of read formatting back into the service,
 * making it shallower (interface nearly as complex as the
 * implementation) rather than merely moving the code.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultFollowInspector implements FollowInspector {

    private final FollowMapper followMapper;
    private final UserMapper userMapper;

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

    @Override
    public boolean isFollowing(String currentUserId, String targetUserId) {
        if (currentUserId.equals(targetUserId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Cannot query follow status of yourself");
        }
        if (userMapper.selectById(targetUserId) == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        return followMapper.exists(currentUserId, targetUserId);
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
}
