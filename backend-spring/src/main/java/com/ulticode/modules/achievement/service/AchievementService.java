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
     * Get user's achievement progress for all achievements as
     * {@link AchievementProgressDTO} (consumed by
     * {@code GET /achievements/my} via {@code AchievementController}).
     *
     * <p>NOTE: This method is NOT a duplicate of
     * {@link #getUserProgress(String)} — the two methods return
     * <em>different</em> DTO shapes for <em>different</em> endpoints:</p>
     * <ul>
     *   <li>This method: {@code List<AchievementProgressDTO>} — carries
     *       {@code description}, {@code points}, {@code earned}, {@code earnedAt}
     *       (the FE achievement page needs all of these). Backs
     *       {@code GET /achievements/my} and {@code GET /achievements/user/{id}}.</li>
     *   <li>{@code getUserProgress}: {@code List<AchievementProgressVO>} — carries
     *       {@code currentValue}, {@code targetValue}, {@code percentage},
     *       {@code nextMilestone} (the FE user-profile page needs computed
     *       progress stats). Backs
     *       {@code GET /users/me/achievements/progress} via
     *       {@code UserController.getAchievementProgress()}.</li>
     * </ul>
     *
     * <p>Both methods share the same pre-fetch strategy (count submissions
     * once, then map per achievement) for SQL efficiency.</p>
     *
     * <p>Previous review (LOW #6) mis-classified the two as duplicates; this
     * Javadoc documents the actual contract. (Tracked in
     * docs/achievement-api-test-report-2026-06-11.md §6 LOW #5.)</p>
     *
     * @param userId the user ID
     * @return list of achievement progress DTOs
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
