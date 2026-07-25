package com.ulticode.modules.achievement.service;

import com.ulticode.modules.achievement.dto.AchievementDTO;
import com.ulticode.modules.achievement.dto.AchievementVO;
import com.ulticode.modules.achievement.entity.Achievement;

/**
 * Write-path service for achievement CRUD.
 *
 * <p>Read paths (list / getById / getUserProgress / getUserAchievements /
 * getUserPoints) live in {@link
 * com.ulticode.modules.achievement.projection.AchievementProjection}
 * &mdash; see ADR-0005. This interface keeps only the state-mutating
 * operations plus {@link #findByKey(String)}, which the write paths use
 * internally for duplicate-key checks.</p>
 */
public interface AchievementService {

    /**
     * Create a new achievement.
     *
     * @param dto the achievement data
     * @return created achievement, projected via
     *     {@code AchievementProjection#toVO(Achievement)}
     */
    AchievementVO create(AchievementDTO dto);

    /**
     * Update an achievement.
     *
     * @param id the achievement id
     * @param dto the update data
     * @return updated achievement, projected via
     *     {@code AchievementProjection#toVO(Achievement)}
     */
    AchievementVO update(String id, AchievementDTO dto);

    /**
     * Delete an achievement and its associated user-achievement rows.
     *
     * @param id the achievement id
     */
    void delete(String id);

    /**
     * Find an achievement by key. Used internally by the write paths for
     * duplicate-key checks.
     *
     * @param key the achievement key
     * @return the achievement or {@code null}
     */
    Achievement findByKey(String key);
}
