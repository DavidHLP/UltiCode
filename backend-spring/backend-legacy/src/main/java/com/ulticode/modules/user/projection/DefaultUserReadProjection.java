package com.ulticode.modules.user.projection;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.response.PageResult;
import com.ulticode.common.response.PaginationRequest;
import com.ulticode.common.auth.CurrentUserProvider;
import com.ulticode.app.api.service.FollowCountPort;
import com.ulticode.app.api.service.ProblemDifficultyReadPort;
import com.ulticode.app.api.service.ProblemTagStatsReadPort;
import com.ulticode.app.api.dto.SubmissionDateCountDTO;
import com.ulticode.app.api.service.SubmissionUserStatsPort;
import com.ulticode.app.api.service.SubmissionStreakPort;
import com.ulticode.app.api.dto.DifficultyCountDTO;
import com.ulticode.modules.user.dto.ProfileVO;
import com.ulticode.modules.user.dto.UserSkillsDTO;
import com.ulticode.modules.user.dto.UserStatsDTO;
import com.ulticode.modules.user.dto.UserVO;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.Year;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Default (and only) adapter for {@link UserReadProjection}. Owns every
 * read-side join for the user domain — see the interface javadoc for why
 * this is a deep module.
 *
 * <p>The simple find-by-* reads delegate straight to {@code UserMapper}.
 * The user-stats read owns the cross-table join across
 * {@code submissions} + {@code problems} + the global-ranking table, plus
 * the heatmap-level bucketing that the deleted {@code UserService} facade
 * used to scatter across a 70-line method. The user-skills read owns the
 * problem-tag-relation join. The user-profile read composes
 * {@code user + stats + follower/following} counts in one place.
 *
 * @author ulticode
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultUserReadProjection implements UserReadProjection {

    private final UserMapper userMapper;
    private final SubmissionUserStatsPort submissionUserStats;
    private final SubmissionStreakPort submissionStreakCalculator;
    private final ProblemDifficultyReadPort problemDifficultyReadPort;
    private final ProblemTagStatsReadPort problemTagStatsReadPort;
    private final FollowCountPort followCountPort;
    private final CurrentUserProvider currentUserProvider;

    @Override
    public Optional<User> findById(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(userMapper.selectById(id));
    }

    @Override
    public Map<String, User> findAllById(Collection<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        return userMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
    }

    @Override
    public Optional<User> findByUsername(String username) {
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getUsername, username);
        return Optional.ofNullable(userMapper.selectOne(queryWrapper));
    }

    @Override
    public Optional<User> findByEmail(String email) {
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getEmail, email);
        return Optional.ofNullable(userMapper.selectOne(queryWrapper));
    }

    @Override
    public UserVO getCurrentUser() {
        String userId = currentUserProvider.getCurrentUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        User user = findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        return toVO(user);
    }

    @Override
    public PageResult<UserVO> listUsers(Integer page, Integer pageSize) {
        PaginationRequest pageRequest = PaginationRequest.of(page, pageSize);

        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getIsActive, true)
                .eq(User::getIsBanned, false)
                .orderByDesc(User::getJoinedAt);

        Page<User> userPage = new Page<>(pageRequest.page(), pageRequest.pageSize());
        Page<User> result = userMapper.selectPage(userPage, queryWrapper);

        List<UserVO> userVOList = result.getRecords().stream()
                .map(this::toPublicVO)
                .collect(Collectors.toList());

        return PageResult.of(userVOList, result.getTotal(), pageRequest);
    }

    @Override
    public UserVO getUserById(String id) {
        User user = findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // Return public profile (without email)
        return toPublicVO(user);
    }

    @Override
    @Cacheable(value = "userStats", key = "'getUserStatsById:' + #id")
    public UserStatsDTO getUserStatsById(String id) {
        // Verify user exists
        if (findById(id).isEmpty()) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        UserStatsDTO stats = new UserStatsDTO();

        // Get solved counts by difficulty
        List<DifficultyCountDTO> solvedByDifficulty = submissionUserStats.countAcceptedProblemsByDifficulty(id);
        Map<String, UserStatsDTO.DifficultyStats> statsMap = new HashMap<>();

        // Initialize with zero counts for all difficulties
        statsMap.put("Easy", new UserStatsDTO.DifficultyStats(0, 0));
        statsMap.put("Medium", new UserStatsDTO.DifficultyStats(0, 0));
        statsMap.put("Hard", new UserStatsDTO.DifficultyStats(0, 0));

        // Populate solved counts
        int totalSolved = 0;
        for (DifficultyCountDTO row : solvedByDifficulty) {
            String difficulty = row.getDifficulty();
            int count = row.getCount().intValue();
            totalSolved += count;
            if (statsMap.containsKey(difficulty)) {
                statsMap.put(difficulty, new UserStatsDTO.DifficultyStats(count, 0));
            }
        }

        // Get total counts by difficulty from problems table
        List<DifficultyCountDTO> totalByDifficulty = problemDifficultyReadPort.countByDifficulty();
        for (DifficultyCountDTO row : totalByDifficulty) {
            String difficulty = row.getDifficulty();
            int total = row.getCount().intValue();
            if (statsMap.containsKey(difficulty)) {
                UserStatsDTO.DifficultyStats current = statsMap.get(difficulty);
                statsMap.put(difficulty, new UserStatsDTO.DifficultyStats(current.getCount(), total));
            }
        }

        stats.setStats(statsMap);
        stats.setTotalSolved(totalSolved);

        // Get streak
        int streak = submissionStreakCalculator.computeStreak(id);
        stats.setStreak(streak);

        // Get heatmap data for current year
        int currentYear = Year.now().getValue();
        List<SubmissionDateCountDTO> heatmapData = submissionUserStats.findSubmissionCountsByDate(id, currentYear);

        // Find max submissions for level calculation
        int maxCount = heatmapData.stream()
                .mapToInt(row -> row.getCount().intValue())
                .max()
                .orElse(1);

        List<UserStatsDTO.HeatmapEntry> heatmap = heatmapData.stream()
                .map(row -> {
                    String date = row.getDate();
                    int count = row.getCount().intValue();
                    // Calculate level (0-4) based on submission count
                    int level = (count == 0) ? 0 : Math.min(4, (int) Math.ceil((double) count / maxCount * 4));
                    return new UserStatsDTO.HeatmapEntry(date, level);
                })
                .collect(Collectors.toList());

        stats.setHeatmap(heatmap);

        // Get global rank from global_rankings
        Integer globalRank = submissionUserStats.findGlobalRankByUserId(id);
        stats.setGlobalRank(globalRank);

        // Get acceptance rate
        Double acceptanceRate = submissionUserStats.calculateAcceptanceRateByUserId(id);
        stats.setAcceptanceRate(acceptanceRate);

        // Get total submission count
        Long submissionCount = submissionUserStats.countTotalSubmissionsByUserId(id);
        stats.setSubmissionCount(submissionCount);

        return stats;
    }

    @Override
    public UserVO toVO(User user) {
        if (user == null) {
            return null;
        }

        UserVO vo = new UserVO();
        BeanUtils.copyProperties(user, vo);
        return vo;
    }

    /**
     * Convert a User entity to UserVO without sensitive information.
     * Used for public profiles.
     *
     * @param user the user entity
     * @return the user view object without email
     */
    private UserVO toPublicVO(User user) {
        if (user == null) {
            return null;
        }

        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setName(user.getName());
        vo.setAvatar(user.getAvatar());
        vo.setBio(user.getBio());
        vo.setCompany(user.getCompany());
        vo.setGithub(user.getGithub());
        vo.setJoinedAt(user.getJoinedAt());
        vo.setLocation(user.getLocation());
        vo.setTwitter(user.getTwitter());
        vo.setWebsite(user.getWebsite());
        vo.setPreferredLanguage(user.getPreferredLanguage());
        // Email is not included in public profile
        return vo;
    }

    @Override
    public UserSkillsDTO getUserSkillsById(String id) {
        // Verify user exists
        if (findById(id).isEmpty()) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        UserSkillsDTO skillsDTO = new UserSkillsDTO();

        // Get tag stats from problem-tag relations via port
        List<Map<String, Object>> tagStats = problemTagStatsReadPort.findTagStatsByUserId(id);
        List<UserSkillsDTO.UserSkill> skills = tagStats.stream()
                .map(this::toUserSkill)
                .toList();

        skillsDTO.setSkills(skills);

        // Calculate total solved (reuse existing logic from getUserStatsById)
        Long totalSolved = submissionUserStats.countAcceptedProblemsByUserId(id);
        skillsDTO.setTotalSolved(totalSolved != null ? totalSolved.intValue() : 0);

        return skillsDTO;
    }

    private UserSkillsDTO.UserSkill toUserSkill(Map<String, Object> row) {
        Object count = row.get("count");
        return new UserSkillsDTO.UserSkill(
                Objects.toString(row.get("tagName"), ""),
                Objects.toString(row.get("tagSlug"), ""),
                count instanceof Number ? ((Number) count).intValue() : 0);
    }

    @Override
    public ProfileVO getUserProfile(String id) {
        User user = findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        UserStatsDTO stats = getUserStatsById(id);

        int followerCount = 0;
        int followingCount = 0;
        try {
            // Follow counts come from the follow module via FollowCountPort —
            // the user module no longer imports FollowMapper directly.
            followerCount = (int) followCountPort.countFollowers(id);
            followingCount = (int) followCountPort.countFollowing(id);
        } catch (Exception e) {
            log.warn("Failed to get follow counts for user {}: {}", id, e.getMessage());
        }

        return ProfileVO.fromUser(user, stats, followerCount, followingCount, 0);
    }

    @Override
    public ProfileVO getUserProfileByUsername(String username) {
        User user = findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return getUserProfile(user.getId());
    }
}
