package com.ulticode.modules.achievement.service;

import com.ulticode.common.response.PageResult;
import com.ulticode.modules.achievement.dto.*;
import com.ulticode.modules.achievement.entity.Achievement;

import java.util.List;

/**
 * Service interface for achievement operations.
 */
public interface AchievementService {

    /**
     * Get user's progress for all achievements (earned and unearned).
     *
     * @param userId the user ID
     * @return list of achievement progress view objects
     */
    List<AchievementProgressVO> getUserProgress(String userId);

    /**
     * Create a new achievement.
     *
     * @param dto the achievement data
     * @return created achievement
     */
    AchievementVO create(AchievementDTO dto);

    /**
     * Get paginated list of achievements.
     *
     * @param query the query parameters
     * @return paginated achievements
     */
    PageResult<AchievementVO> list(AchievementQueryDTO query);

    /**
     * Get a single achievement by ID.
     *
     * @param id the achievement ID
     * @return the achievement
     */
    AchievementVO getById(String id);

    /**
     * Update an achievement.
     *
     * @param id the achievement ID
     * @param dto the update data
     * @return updated achievement
     */
    AchievementVO update(String id, AchievementDTO dto);

    /**
     * Delete an achievement.
     *
     * @param id the achievement ID
     */
    void delete(String id);

    /**
     * Get user's achievement progress for all achievements.
     *
     * @param userId the user ID
     * @return list of achievement progress
     */
    List<AchievementProgressDTO> getUserAchievements(String userId);

    /**
     * Get user's total achievement points.
     *
     * @param userId the user ID
     * @return the user's points
     */
    UserPointsVO getUserPoints(String userId);

    /**
     * Find achievement by key.
     *
     * @param key the achievement key
     * @return the achievement or null
     */
    Achievement findByKey(String key);
}
