package com.ulticode.modules.recommendation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ulticode.modules.recommendation.entity.DailyRecommendation;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * MyBatis-Plus mapper for DailyRecommendation entity.
 */
@Mapper
public interface DailyRecommendationMapper extends BaseMapper<DailyRecommendation> {

    /**
     * Find recommendations for a user by scenario and date range.
     *
     * @param userId    the user ID
     * @param scenario  the recommendation scenario
     * @param startDate start of date range
     * @param endDate   end of date range
     * @return list of recommendations
     */
    @Select("SELECT * FROM daily_recommendations " +
            "WHERE user_id = #{userId} " +
            "AND scenario = #{scenario} " +
            "AND generated_at BETWEEN #{startDate} AND #{endDate} " +
            "ORDER BY score DESC")
    List<DailyRecommendation> findByUserAndScenarioAndDateRange(
            @Param("userId") String userId,
            @Param("scenario") String scenario,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    /**
     * Find today's recommendations for a user.
     *
     * @param userId   the user ID
     * @param scenario the recommendation scenario
     * @param today    start of today
     * @return list of today's recommendations
     */
    @Select("SELECT * FROM daily_recommendations " +
            "WHERE user_id = #{userId} " +
            "AND scenario = #{scenario} " +
            "AND generated_at >= #{today} " +
            "ORDER BY score DESC")
    List<DailyRecommendation> findTodayByUserAndScenario(
            @Param("userId") String userId,
            @Param("scenario") String scenario,
            @Param("today") LocalDateTime today);

    /**
     * Delete old recommendations before a certain date.
     *
     * @param beforeDate the cutoff date
     * @return number of deleted records
     */
    @Delete("DELETE FROM daily_recommendations WHERE generated_at < #{beforeDate}")
    int deleteOldRecommendations(@Param("beforeDate") LocalDateTime beforeDate);

    /**
     * Mark a recommendation as clicked by the user.
     */
    @Update("UPDATE daily_recommendations SET is_clicked = 1 WHERE id = #{id}")
    void updateClicked(@Param("id") String id);

    /**
     * Mark a recommendation as solved by the user.
     */
    @Update("UPDATE daily_recommendations SET is_solved = 1 WHERE id = #{id}")
    void updateSolved(@Param("id") String id);

    /**
     * Delete expired recommendations.
     */
    @Delete("DELETE FROM daily_recommendations WHERE expires_at < #{now}")
    int deleteExpired(@Param("now") LocalDateTime now);
}
