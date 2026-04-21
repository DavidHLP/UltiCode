package com.ulticode.modules.achievement.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.response.PageResult;
import com.ulticode.modules.achievement.constants.AchievementType;
import com.ulticode.modules.achievement.dto.*;
import com.ulticode.modules.achievement.entity.Achievement;
import com.ulticode.modules.achievement.entity.UserAchievement;
import com.ulticode.modules.achievement.mapper.AchievementMapper;
import com.ulticode.modules.achievement.mapper.UserAchievementMapper;
import com.ulticode.modules.achievement.service.AchievementService;
import com.ulticode.modules.contest.mapper.ContestParticipantMapper;
import com.ulticode.modules.submission.mapper.SubmissionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Implementation of AchievementService.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AchievementServiceImpl implements AchievementService {

    private final AchievementMapper achievementMapper;
    private final UserAchievementMapper userAchievementMapper;
    private final SubmissionMapper submissionMapper;
    private final ContestParticipantMapper contestParticipantMapper;

    @Override
    public List<AchievementProgressVO> getUserProgress(String userId) {
        List<Achievement> achievements = achievementMapper.findAllActive();
        List<UserAchievement> userAchievements = userAchievementMapper.findByUserId(userId);

        // Build earned achievement IDs set
        Map<String, UserAchievement> earnedMap = userAchievements.stream()
                .collect(Collectors.toMap(UserAchievement::getAchievementId, ua -> ua));

        // Pre-fetch current counts for progress calculation
        long problemsSolved = submissionMapper.countAcceptedProblemsByUserId(userId);
        long submissionsMade = submissionMapper.countByUserId(userId);

        return achievements.stream()
                .map(a -> buildProgressVO(a, earnedMap.get(a.getId()), problemsSolved, submissionsMade))
                .collect(Collectors.toList());
    }

    private AchievementProgressVO buildProgressVO(Achievement a, UserAchievement earned,
            long problemsSolved, long submissionsMade) {
        String type = getTypeFromCriteria(a.getCriteria());
        int currentValue = calculateCurrentValue(type, earned, problemsSolved, submissionsMade);
        int target = getTargetFromCriteria(a.getCriteria());
        int percentage = target > 0 ? Math.min(100, (currentValue * 100) / target) : 0;
        String nextMilestone = calculateNextMilestone(type, currentValue);

        return new AchievementProgressVO(
                a.getId(),
                a.getKey(),
                a.getName(),
                a.getIcon(),
                a.getTier(),
                a.getCategory(),
                currentValue,
                target,
                percentage,
                nextMilestone
        );
    }

    private String getTypeFromCriteria(Map<String, Object> criteria) {
        if (criteria == null) {
            return null;
        }
        return (String) criteria.get("type");
    }

    private int getTargetFromCriteria(Map<String, Object> criteria) {
        if (criteria == null || !criteria.containsKey("target")) {
            return 0;
        }
        Object targetObj = criteria.get("target");
        if (targetObj instanceof Number) {
            return ((Number) targetObj).intValue();
        }
        return 0;
    }

    private int calculateCurrentValue(String type, UserAchievement earned,
            long problemsSolved, long submissionsMade) {
        if (type == null) {
            return 0;
        }

        int currentValue = switch (type) {
            case "problems_solved" -> (int) problemsSolved;
            case "submissions_made" -> (int) submissionsMade;
            default -> 0;
        };

        return currentValue;
    }

    private String calculateNextMilestone(String type, int currentValue) {
        if (type == null) {
            return null;
        }

        return switch (type) {
            case "problems_solved" -> {
                int[] milestones = {1, 10, 50, 100, 200, 500};
                for (int m : milestones) {
                    if (currentValue < m) {
                        yield m + " problems";
                    }
                }
                yield "Max milestone reached";
            }
            case "submissions_made" -> {
                int[] milestones = {1, 10, 50, 100, 500, 1000};
                for (int m : milestones) {
                    if (currentValue < m) {
                        yield m + " submissions";
                    }
                }
                yield "Max milestone reached";
            }
            default -> null;
        };
    }

    @Override
    @Transactional
    public AchievementVO create(AchievementDTO dto) {
        // Check if key already exists
        Achievement existing = achievementMapper.findByKey(dto.getKey());
        if (existing != null) {
            throw new BusinessException(ErrorCode.CONFLICT, "Achievement with key '" + dto.getKey() + "' already exists");
        }

        Achievement achievement = new Achievement();
        achievement.setKey(dto.getKey());
        achievement.setName(dto.getName());
        achievement.setDescription(dto.getDescription());
        achievement.setIcon(dto.getIcon());
        achievement.setCategory(dto.getCategory());
        achievement.setTier(dto.getTier());
        achievement.setCriteria(dto.getCriteria());
        achievement.setPoints(dto.getPoints());
        achievement.setIsActive(dto.getIsActive() != null ? dto.getIsActive() : true);

        achievementMapper.insert(achievement);
        log.info("Created achievement: {} with key: {}", achievement.getName(), achievement.getKey());

        return toVO(achievement);
    }

    @Override
    public PageResult<AchievementVO> list(AchievementQueryDTO query) {
        // Validate category if provided
        if (query.getCategory() != null && !query.getCategory().isEmpty()) {
            List<String> validCategories = List.of("problems", "contests", "social", "streaks", "special");
            if (!validCategories.contains(query.getCategory())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "Invalid category. Must be one of: " + validCategories);
            }
        }

        LambdaQueryWrapper<Achievement> wrapper = new LambdaQueryWrapper<>();

        if (query.getCategory() != null && !query.getCategory().isEmpty()) {
            wrapper.eq(Achievement::getCategory, query.getCategory());
        }
        if (query.getTier() != null) {
            wrapper.eq(Achievement::getTier, query.getTier());
        }
        if (query.getIsActive() != null) {
            wrapper.eq(Achievement::getIsActive, query.getIsActive());
        } else {
            wrapper.eq(Achievement::getIsActive, true);
        }

        wrapper.orderByAsc(Achievement::getCategory)
               .orderByAsc(Achievement::getTier);

        Page<Achievement> page = new Page<>(query.getPage(), query.getLimit());
        Page<Achievement> result = achievementMapper.selectPage(page, wrapper);

        List<AchievementVO> voList = result.getRecords().stream()
                .map(this::toVO)
                .collect(Collectors.toList());

        return PageResult.of(voList, result.getTotal(), query.getPage(), query.getLimit());
    }

    @Override
    public AchievementVO getById(String id) {
        Achievement achievement = achievementMapper.selectById(id);
        if (achievement == null) {
            throw new BusinessException(ErrorCode.ACHIEVEMENT_NOT_FOUND, "Achievement not found");
        }
        return toVO(achievement);
    }

    @Override
    @Transactional
    public AchievementVO update(String id, AchievementDTO dto) {
        Achievement achievement = achievementMapper.selectById(id);
        if (achievement == null) {
            throw new BusinessException(ErrorCode.ACHIEVEMENT_NOT_FOUND, "Achievement not found");
        }

        // Check key uniqueness if changing
        if (dto.getKey() != null && !dto.getKey().equals(achievement.getKey())) {
            Achievement existing = achievementMapper.findByKey(dto.getKey());
            if (existing != null) {
                throw new BusinessException(ErrorCode.CONFLICT, "Achievement with key '" + dto.getKey() + "' already exists");
            }
            achievement.setKey(dto.getKey());
        }

        if (dto.getName() != null) {
            achievement.setName(dto.getName());
        }
        if (dto.getDescription() != null) {
            achievement.setDescription(dto.getDescription());
        }
        if (dto.getIcon() != null) {
            achievement.setIcon(dto.getIcon());
        }
        if (dto.getCategory() != null) {
            achievement.setCategory(dto.getCategory());
        }
        if (dto.getTier() != null) {
            achievement.setTier(dto.getTier());
        }
        if (dto.getCriteria() != null) {
            achievement.setCriteria(dto.getCriteria());
        }
        if (dto.getPoints() != null) {
            achievement.setPoints(dto.getPoints());
        }
        if (dto.getIsActive() != null) {
            achievement.setIsActive(dto.getIsActive());
        }

        achievementMapper.updateById(achievement);
        log.info("Updated achievement: {}", achievement.getName());

        return toVO(achievement);
    }

    @Override
    @Transactional
    public void delete(String id) {
        Achievement achievement = achievementMapper.selectById(id);
        if (achievement == null) {
            throw new BusinessException(ErrorCode.ACHIEVEMENT_NOT_FOUND, "Achievement not found");
        }

        // Delete associated user achievements
        LambdaQueryWrapper<UserAchievement> uaWrapper = new LambdaQueryWrapper<>();
        uaWrapper.eq(UserAchievement::getAchievementId, id);
        userAchievementMapper.delete(uaWrapper);

        achievementMapper.deleteById(id);
        log.info("Deleted achievement: {}", achievement.getName());
    }

    @Override
    public List<AchievementProgressDTO> getUserAchievements(String userId) {
        List<Achievement> achievements = achievementMapper.findAllActive();

        Map<String, UserAchievement> earnedMap = userAchievementMapper.findByUserId(userId)
                .stream()
                .collect(Collectors.toMap(UserAchievement::getAchievementId, ua -> ua));

        return achievements.stream()
                .map(a -> {
                    AchievementProgressDTO dto = new AchievementProgressDTO();
                    dto.setAchievementId(a.getId());
                    dto.setKey(a.getKey());
                    dto.setName(a.getName());
                    dto.setDescription(a.getDescription());
                    dto.setIcon(a.getIcon());
                    dto.setCategory(a.getCategory());
                    dto.setTier(a.getTier());
                    dto.setPoints(a.getPoints());

                    UserAchievement earned = earnedMap.get(a.getId());
                    dto.setEarned(earned != null);
                    dto.setEarnedAt(earned != null ? earned.getEarnedAt() : null);

                    // Extract target from criteria
                    Map<String, Object> criteria = a.getCriteria();
                    if (criteria != null && criteria.containsKey("target")) {
                        Object targetObj = criteria.get("target");
                        if (targetObj instanceof Number) {
                            dto.setTarget(((Number) targetObj).intValue());
                        }
                    }
                    dto.setProgress(0);

                    return dto;
                })
                .collect(Collectors.toList());
    }

    @Override
    public UserPointsVO getUserPoints(String userId) {
        List<UserAchievement> userAchievements = userAchievementMapper.findByUserId(userId);

        int totalPoints = 0;
        for (UserAchievement ua : userAchievements) {
            Achievement achievement = achievementMapper.selectById(ua.getAchievementId());
            if (achievement != null && achievement.getPoints() != null) {
                totalPoints += achievement.getPoints();
            }
        }

        return new UserPointsVO(totalPoints, userAchievements.size());
    }

    @Override
    public Achievement findByKey(String key) {
        return achievementMapper.findByKey(key);
    }

    // ==================== Private Helper Methods ====================

    private AchievementVO toVO(Achievement achievement) {
        AchievementVO vo = new AchievementVO();
        vo.setId(achievement.getId());
        vo.setKey(achievement.getKey());
        vo.setName(achievement.getName());
        vo.setDescription(achievement.getDescription());
        vo.setIcon(achievement.getIcon());
        vo.setCategory(achievement.getCategory());
        vo.setTier(achievement.getTier());
        vo.setCriteria(achievement.getCriteria());
        vo.setPoints(achievement.getPoints());
        vo.setIsActive(achievement.getIsActive());
        vo.setCreatedAt(achievement.getCreatedAt());
        vo.setUpdatedAt(achievement.getUpdatedAt());
        return vo;
    }
}
