package com.ulticode.modules.user.projection;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.response.PageResult;
import com.ulticode.common.util.SecurityUtil;
import com.ulticode.modules.follow.mapper.FollowMapper;
import com.ulticode.modules.problem.mapper.ProblemMapper;
import com.ulticode.modules.problem.mapper.ProblemTagRelationMapper;
import com.ulticode.modules.submission.dto.SubmissionDateCountDTO;
import com.ulticode.modules.submission.mapper.SubmissionMapper;
import com.ulticode.modules.user.dto.DifficultyCountDTO;
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
    private final SubmissionMapper submissionMapper;
    private final ProblemMapper problemMapper;
    private final ProblemTagRelationMapper problemTagRelationMapper;
    private final FollowMapper followMapper;

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
        String userId = SecurityUtil.getCurrentUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        User user = findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        return toVO(user);
    }

    @Override
    public PageResult<UserVO> listUsers(Integer page, Integer pageSize) {
        // Set default pagination values
        int currentPage = (page != null && page > 0) ? page : 1;
        int currentPageSize = (pageSize != null && pageSize > 0) ? pageSize : 20;

        // Limit page size to prevent large queries
        currentPageSize = Math.min(currentPageSize, 100);

        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getIsActive, true)
                .eq(User::getIsBanned, false)
                .orderByDesc(User::getJoinedAt);

        Page<User> userPage = new Page<>(currentPage, currentPageSize);
        Page<User> result = userMapper.selectPage(userPage, queryWrapper);

        List<UserVO> userVOList = result.getRecords().stream()
                .map(this::toPublicVO)
                .collect(Collectors.toList());

        return PageResult.of(userVOList, result.getTotal(), currentPage, currentPageSize);
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
        List<DifficultyCountDTO> solvedByDifficulty = submissionMapper.countAcceptedProblemsByDifficulty(id);
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
        List<DifficultyCountDTO> totalByDifficulty = problemMapper.countByDifficulty();
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
        Integer streak = submissionMapper.calculateStreak(id);
        stats.setStreak(streak != null ? streak : 0);

        // Get heatmap data for current year
        int currentYear = Year.now().getValue();
        List<SubmissionDateCountDTO> heatmapData = submissionMapper.findSubmissionCountsByDate(id, currentYear);

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
        Integer globalRank = submissionMapper.findGlobalRankByUserId(id);
        stats.setGlobalRank(globalRank);

        // Get acceptance rate
        Double acceptanceRate = submissionMapper.calculateAcceptanceRateByUserId(id);
        stats.setAcceptanceRate(acceptanceRate);

        // Get total submission count
        Long submissionCount = submissionMapper.countTotalSubmissionsByUserId(id);
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

        // Get tag stats from problem_tag_relations
        List<Map<String, Object>> tagStats = Optional
                .ofNullable(problemTagRelationMapper.findTagStatsByUserId(id))
                .orElse(List.of());
        List<UserSkillsDTO.UserSkill> skills = tagStats.stream()
                .map(this::toUserSkill)
                .toList();

        skillsDTO.setSkills(skills);

        // Calculate total solved (reuse existing logic from getUserStatsById)
        Long totalSolved = submissionMapper.countAcceptedProblemsByUserId(id);
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
            if (followMapper != null) {
                // countByFollowingId: how many users follow this user (follower count)
                followerCount = followMapper.countByFollowingId(id);
                // countByFollowerId: how many users this user follows (following count)
                followingCount = followMapper.countByFollowerId(id);
            }
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