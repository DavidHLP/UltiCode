package com.ulticode.modules.achievement.projection;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.response.PageResult;
import com.ulticode.modules.achievement.dto.AchievementProgressDTO;
import com.ulticode.modules.achievement.dto.AchievementProgressVO;
import com.ulticode.modules.achievement.dto.AchievementQueryDTO;
import com.ulticode.modules.achievement.dto.AchievementVO;
import com.ulticode.modules.achievement.dto.UserPointsVO;
import com.ulticode.modules.achievement.entity.Achievement;
import com.ulticode.modules.achievement.entity.UserAchievement;
import com.ulticode.modules.achievement.mapper.AchievementMapper;
import com.ulticode.modules.achievement.mapper.UserAchievementMapper;
import com.ulticode.modules.submission.mapper.SubmissionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Default (and only) adapter for {@link AchievementProjection}. Owns every
 * entity&rarr;VO projection rule and read-side aggregation for the achievement
 * domain.
 *
 * <p>Behaviour is byte-for-byte identical to the read paths previously inlined
 * in {@code AchievementServiceImpl}; the only change is locality (see
 * ADR-0005). The {@link SubmissionMapper} dependency &mdash; used solely by
 * the progress counters &mdash; moved here from the service; the service's
 * unused {@code ContestParticipantMapper} field was dropped entirely.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultAchievementProjection implements AchievementProjection {

    private final AchievementMapper achievementMapper;
    private final UserAchievementMapper userAchievementMapper;
    private final SubmissionMapper submissionMapper;

    @Override
    public AchievementVO getById(String id) {
        Achievement achievement = achievementMapper.selectById(id);
        if (achievement == null) {
            throw new BusinessException(ErrorCode.ACHIEVEMENT_NOT_FOUND, "Achievement not found");
        }
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

    @Override
    public List<AchievementProgressDTO> getUserAchievements(String userId) {
        List<Achievement> achievements = achievementMapper.findAllActive();

        Map<String, UserAchievement> earnedMap = userAchievementMapper.findByUserId(userId)
                .stream()
                .collect(Collectors.toMap(UserAchievement::getAchievementId, ua -> ua));

        // Pre-fetch current counts for progress calculation (mirror getUserProgress)
        long problemsSolved = submissionMapper.countAcceptedProblemsByUserId(userId);
        long submissionsMade = submissionMapper.countByUserId(userId);

        return achievements.stream()
                .map(a -> buildProgressDTO(a, earnedMap, problemsSolved, submissionsMade))
                .collect(Collectors.toList());
    }

    @Override
    public UserPointsVO getUserPoints(String userId) {
        List<UserAchievement> userAchievements = userAchievementMapper.findByUserId(userId);

        if (userAchievements.isEmpty()) {
            return new UserPointsVO(0, 0);
        }

        List<String> achievementIds = userAchievements.stream()
                .map(UserAchievement::getAchievementId)
                .collect(Collectors.toList());

        Map<String, Achievement> achievementMap = achievementMapper.selectBatchIds(achievementIds)
                .stream()
                .collect(Collectors.toMap(Achievement::getId, a -> a));

        int totalPoints = 0;
        for (UserAchievement ua : userAchievements) {
            Achievement achievement = achievementMap.get(ua.getAchievementId());
            if (achievement != null && achievement.getPoints() != null) {
                totalPoints += achievement.getPoints();
            }
        }

        return new UserPointsVO(totalPoints, userAchievements.size());
    }

    @Override
    public AchievementVO toVO(Achievement achievement) {
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

    // ==================== Private projection helpers ====================

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

    /**
     * Build a single progress DTO from an achievement plus the user's earned
     * map and pre-fetched submission counters.
     *
     * <p>{@code criteria} may be {@code null} if the row was inserted with a
     * non-JSON {@code criteria} column value (legacy seed data) &mdash; in that
     * case both {@code target} and {@code currentValue} fall back to 0.</p>
     */
    private AchievementProgressDTO buildProgressDTO(Achievement a,
            Map<String, UserAchievement> earnedMap,
            long problemsSolved, long submissionsMade) {
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

        int target = 0;
        int currentValue = 0;
        Map<String, Object> criteria = a.getCriteria();
        if (criteria != null) {
            Object targetObj = criteria.get("target");
            if (targetObj instanceof Number) {
                target = ((Number) targetObj).intValue();
            }
            Object typeObj = criteria.get("type");
            if ("problems_solved".equals(typeObj)) {
                currentValue = (int) problemsSolved;
            } else if ("submissions_made".equals(typeObj)) {
                currentValue = (int) submissionsMade;
            }
        }
        dto.setTarget(target);
        dto.setProgress(currentValue);
        return dto;
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
}
