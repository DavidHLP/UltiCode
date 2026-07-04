package com.ulticode.modules.achievement.projection;

import com.ulticode.common.response.PageResult;
import com.ulticode.modules.achievement.dto.AchievementProgressDTO;
import com.ulticode.modules.achievement.dto.AchievementProgressVO;
import com.ulticode.modules.achievement.dto.AchievementQueryDTO;
import com.ulticode.modules.achievement.dto.AchievementVO;
import com.ulticode.modules.achievement.dto.UserPointsVO;
import com.ulticode.modules.achievement.entity.Achievement;

import java.util.List;

/**
 * Deep module owning every entity&rarr;VO projection and read-side aggregation
 * for the achievement domain, behind a small interface.
 *
 * <p>Controllers depend on it directly for reads; {@link
 * com.ulticode.modules.achievement.service.AchievementService} keeps the write
 * paths (create / update / delete), delegating to this projection for the
 * post-action view shape via {@link #toVO(Achievement)}.</p>
 *
 * <p>Mirrors the projection deep modules already established in this codebase
 * &mdash; {@code ModerationProjection} (ADR-0004), {@code ProblemProjection},
 * {@code SubmissionProjection}, {@code SearchReadProjection},
 * {@code SolutionProjection}, {@code ContestProjection}. See ADR-0005.</p>
 */
public interface AchievementProjection {

    /**
     * Get a single achievement by id, projected to VO.
     *
     * @param id the achievement id
     * @return the projected achievement
     * @throws com.ulticode.common.exception.BusinessException
     *     {@code ACHIEVEMENT_NOT_FOUND} if the row is absent
     */
    AchievementVO getById(String id);

    /**
     * Paginated achievement list with category / tier / isActive filters.
     *
     * @param query the query parameters
     * @return paginated projected achievements
     */
    PageResult<AchievementVO> list(AchievementQueryDTO query);

    /**
     * User progress for every active achievement, carrying the computed
     * {@code currentValue} / {@code targetValue} / {@code percentage} /
     * {@code nextMilestone}. Backs the user-profile progress endpoint.
     *
     * @param userId the user id
     * @return list of achievement progress view objects
     */
    List<AchievementProgressVO> getUserProgress(String userId);

    /**
     * User achievements with {@code earned} / {@code earnedAt} /
     * {@code progress} / {@code target}. Backs the achievement-page endpoint.
     *
     * @param userId the user id
     * @return list of achievement progress DTOs
     */
    List<AchievementProgressDTO> getUserAchievements(String userId);

    /**
     * Total points + count of earned achievements for a user.
     *
     * @param userId the user id
     * @return the user's points summary
     */
    UserPointsVO getUserPoints(String userId);

    /**
     * Entity&rarr;VO facade for the service write paths (post-create /
     * post-update view), mirroring {@code ModerationProjection#toAppealVO}.
     *
     * @param achievement the entity to project (must not be {@code null})
     * @return the projected VO
     */
    AchievementVO toVO(Achievement achievement);
}
