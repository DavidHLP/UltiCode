package com.ulticode.modules.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.response.PageResult;
import com.ulticode.common.util.SecurityUtil;
import com.ulticode.modules.problem.mapper.ProblemMapper;
import com.ulticode.modules.problem.mapper.ProblemTagRelationMapper;
import com.ulticode.modules.submission.mapper.SubmissionMapper;
import com.ulticode.modules.user.dto.DifficultyCountDTO;
import com.ulticode.modules.user.dto.UpdateUserDTO;
import com.ulticode.modules.user.dto.UserSkillsDTO;
import com.ulticode.modules.user.dto.UserStatsDTO;
import com.ulticode.modules.user.dto.UserVO;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.user.mapper.UserMapper;
import com.ulticode.modules.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.Year;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Implementation of UserService.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final SubmissionMapper submissionMapper;
    private final ProblemMapper problemMapper;
    private final ProblemTagRelationMapper problemTagRelationMapper;

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
    @Transactional
    public UserVO updateCurrentUser(UpdateUserDTO updateDTO) {
        String userId = SecurityUtil.getCurrentUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        User user = findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // Check if email is being changed and if it's already taken
        if (updateDTO.getEmail() != null && !updateDTO.getEmail().equals(user.getEmail())) {
            Optional<User> existingUser = findByEmail(updateDTO.getEmail());
            if (existingUser.isPresent()) {
                throw new BusinessException(ErrorCode.AUTH_EMAIL_TAKEN);
            }
        }

        // Update fields from DTO (only non-null fields)
        if (updateDTO.getName() != null) {
            user.setName(updateDTO.getName());
        }
        if (updateDTO.getEmail() != null) {
            user.setEmail(updateDTO.getEmail());
        }
        if (updateDTO.getAvatar() != null) {
            user.setAvatar(updateDTO.getAvatar());
        }
        if (updateDTO.getBio() != null) {
            user.setBio(updateDTO.getBio());
        }
        if (updateDTO.getCompany() != null) {
            user.setCompany(updateDTO.getCompany());
        }
        if (updateDTO.getGithub() != null) {
            user.setGithub(updateDTO.getGithub());
        }
        if (updateDTO.getLocation() != null) {
            user.setLocation(updateDTO.getLocation());
        }
        if (updateDTO.getTwitter() != null) {
            user.setTwitter(updateDTO.getTwitter());
        }
        if (updateDTO.getWebsite() != null) {
            user.setWebsite(updateDTO.getWebsite());
        }
        if (updateDTO.getPreferredLanguage() != null) {
            user.setPreferredLanguage(updateDTO.getPreferredLanguage());
        }

        userMapper.updateById(user);

        log.info("User profile updated: {}", userId);
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
        List<Object[]> heatmapData = submissionMapper.findSubmissionCountsByDate(id, currentYear);

        // Find max submissions for level calculation
        int maxCount = heatmapData.stream()
                .mapToInt(row -> ((Number) row[1]).intValue())
                .max()
                .orElse(1);

        List<UserStatsDTO.HeatmapEntry> heatmap = heatmapData.stream()
                .map(row -> {
                    String date = (String) row[0];
                    int count = ((Number) row[1]).intValue();
                    // Calculate level (0-4) based on submission count
                    int level = (count == 0) ? 0 : Math.min(4, (int) Math.ceil((double) count / maxCount * 4));
                    return new UserStatsDTO.HeatmapEntry(date, level);
                })
                .collect(Collectors.toList());

        stats.setHeatmap(heatmap);

        return stats;
    }

    @Override
    @Transactional
    public void updateLastLoginAt(String userId) {
        if (userId == null || userId.isBlank()) {
            return;
        }

        User user = new User();
        user.setId(userId);
        user.setLastLoginAt(LocalDateTime.now());
        userMapper.updateById(user);

        log.debug("Updated last login time for user: {}", userId);
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
        List<Object[]> tagStats = problemTagRelationMapper.findTagStatsByUserId(id);
        List<UserSkillsDTO.UserSkill> skills = tagStats.stream()
                .map(row -> new UserSkillsDTO.UserSkill(
                        (String) row[0],
                        (String) row[1],
                        ((Number) row[2]).intValue()))
                .toList();

        skillsDTO.setSkills(skills);

        // Calculate total solved (reuse existing logic from getUserStatsById)
        Long totalSolved = submissionMapper.countAcceptedProblemsByUserId(id);
        skillsDTO.setTotalSolved(totalSolved != null ? totalSolved.intValue() : 0);

        return skillsDTO;
    }
}
