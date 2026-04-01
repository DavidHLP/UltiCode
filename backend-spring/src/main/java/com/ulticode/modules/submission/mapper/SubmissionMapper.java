package com.ulticode.modules.submission.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ulticode.modules.submission.entity.Submission;
import com.ulticode.modules.user.dto.DifficultyCountDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Optional;

/**
 * Mapper interface for Submission entity.
 * Extends MyBatis-Plus BaseMapper for basic CRUD operations.
 */
@Mapper
public interface SubmissionMapper extends BaseMapper<Submission> {

    /**
     * Find submissions by user ID with pagination.
     *
     * @param page   pagination object
     * @param userId user ID
     * @return paginated submissions
     */
    @Select("SELECT * FROM submissions WHERE user_id = #{userId} ORDER BY created_at DESC")
    IPage<Submission> findByUserId(Page<Submission> page, @Param("userId") String userId);

    /**
     * Find submissions by problem ID with pagination.
     *
     * @param page     pagination object
     * @param problemId problem ID
     * @param userId   user ID (optional filter)
     * @return paginated submissions
     */
    @Select("<script>" +
            "SELECT * FROM submissions WHERE problem_id = #{problemId} " +
            "<if test='userId != null'> AND user_id = #{userId}</if>" +
            " ORDER BY created_at DESC" +
            "</script>")
    IPage<Submission> findByProblemId(Page<Submission> page,
                                       @Param("problemId") Long problemId,
                                       @Param("userId") String userId);

    /**
     * Find the best (fastest accepted) submission for a problem by user.
     *
     * @param problemId problem ID
     * @param userId    user ID
     * @return the best submission if found
     */
    @Select("SELECT * FROM submissions WHERE problem_id = #{problemId} AND user_id = #{userId} " +
            "AND status = 'Accepted' ORDER BY runtime ASC, memory ASC, created_at DESC LIMIT 1")
    Optional<Submission> findBestByProblemIdAndUserId(@Param("problemId") Long problemId,
                                                       @Param("userId") String userId);

    /**
     * Find submissions by user ID and problem ID.
     *
     * @param userId    user ID
     * @param problemId problem ID
     * @return list of submissions
     */
    @Select("SELECT * FROM submissions WHERE user_id = #{userId} AND problem_id = #{problemId} " +
            "ORDER BY created_at DESC")
    List<Submission> findByUserIdAndProblemId(@Param("userId") String userId,
                                               @Param("problemId") Long problemId);

    /**
     * Count accepted submissions by user.
     *
     * @param userId user ID
     * @return count of accepted submissions
     */
    @Select("SELECT COUNT(DISTINCT problem_id) FROM submissions WHERE user_id = #{userId} AND status = 'Accepted'")
    Long countAcceptedProblemsByUserId(@Param("userId") String userId);

    /**
     * Count total submissions by user.
     *
     * @param userId user ID
     * @return count of total submissions
     */
    @Select("SELECT COUNT(*) FROM submissions WHERE user_id = #{userId}")
    Long countByUserId(@Param("userId") String userId);

    /**
     * Find distinct submission dates for a user in a given year.
     *
     * @param userId user ID
     * @param year   the year to filter by
     * @return list of date strings (YYYY-MM-DD format)
     */
    @Select("SELECT DISTINCT DATE_FORMAT(created_at, '%Y-%m-%d') as date FROM submissions " +
            "WHERE user_id = #{userId} AND YEAR(created_at) = #{year} ORDER BY date")
    List<String> findSubmissionDatesByYear(@Param("userId") String userId, @Param("year") Integer year);

    /**
     * Count accepted submissions by user grouped by problem difficulty.
     * Returns a map of difficulty -> count of DISTINCT solved problems.
     *
     * @param userId user ID
     * @return list of DifficultyCountDTO containing [difficulty, count]
     */
    @Select("SELECT p.difficulty, COUNT(DISTINCT s.problem_id) as count " +
            "FROM submissions s " +
            "JOIN problems p ON s.problem_id = p.id " +
            "WHERE s.user_id = #{userId} AND s.status = 'Accepted' AND p.is_deleted = false " +
            "GROUP BY p.difficulty")
    List<DifficultyCountDTO> countAcceptedProblemsByDifficulty(@Param("userId") String userId);

    /**
     * Find submission dates with counts for heatmap.
     * Returns dates and the number of submissions on each date.
     *
     * @param userId user ID
     * @param year   the year to filter by
     * @return list of Object arrays containing [date, count]
     */
    @Select("SELECT DATE_FORMAT(created_at, '%Y-%m-%d') as date, COUNT(*) as count " +
            "FROM submissions " +
            "WHERE user_id = #{userId} AND YEAR(created_at) = #{year} " +
            "GROUP BY DATE_FORMAT(created_at, '%Y-%m-%d') " +
            "ORDER BY date")
    List<Object[]> findSubmissionCountsByDate(@Param("userId") String userId, @Param("year") Integer year);

    /**
     * Calculate the current streak (consecutive days with submissions ending today/yesterday).
     *
     * @param userId user ID
     * @return number of consecutive days with submissions
     */
    @Select("WITH RECURSIVE dates AS ( " +
            "  SELECT CURDATE() as date, 1 as day_num " +
            "  UNION ALL " +
            "  SELECT DATE_SUB(date, INTERVAL 1 DAY), day_num + 1 " +
            "  FROM dates " +
            "  WHERE day_num <= 365 " +
            "), " +
            "submission_dates AS ( " +
            "  SELECT DISTINCT DATE(created_at) as date " +
            "  FROM submissions " +
            "  WHERE user_id = #{userId} AND created_at >= DATE_SUB(CURDATE(), INTERVAL 365 DAY) " +
            "), " +
            "streak_calc AS ( " +
            "  SELECT d.date, " +
            "         ROW_NUMBER() OVER (ORDER BY d.date DESC) as rn, " +
            "         DATEDIFF(CURDATE(), d.date) as days_ago " +
            "  FROM dates d " +
            "  LEFT JOIN submission_dates sd ON d.date = sd.date " +
            "  WHERE sd.date IS NOT NULL " +
            ") " +
            "SELECT MIN(days_ago) FROM streak_calc WHERE days_ago <= 1")
    Integer calculateStreak(@Param("userId") String userId);

    /**
     * Find monthly submission statistics for a user.
     *
     * @param userId user ID
     * @return list of Object arrays containing [month, totalCount, acceptedCount]
     */
    @Select("SELECT DATE_FORMAT(created_at, '%Y-%m') as month, " +
            "COUNT(*) as total_count, " +
            "SUM(CASE WHEN status = 'Accepted' THEN 1 ELSE 0 END) as accepted_count " +
            "FROM submissions WHERE user_id = #{userId} " +
            "GROUP BY DATE_FORMAT(created_at, '%Y-%m') " +
            "ORDER BY month DESC")
    List<Object[]> findMonthlySubmissionStats(@Param("userId") String userId);

    /**
     * Find submission statistics by programming language for a user.
     *
     * @param userId user ID
     * @return list of Object arrays containing [language, count]
     */
    @Select("SELECT language, COUNT(*) as count FROM submissions " +
            "WHERE user_id = #{userId} " +
            "GROUP BY language " +
            "ORDER BY count DESC")
    List<Object[]> findLanguageStats(@Param("userId") String userId);

    /**
     * Find weekly progress (solved problems per week) for a user.
     *
     * @param userId user ID
     * @return list of Object arrays containing [week, solvedCount, timeSpentHours]
     */
    @Select("SELECT CONCAT(DATE_FORMAT(DATE_SUB(created_at, INTERVAL WEEKDAY(created_at) DAY), '%Y-%m-%d'), ' to ', " +
            "DATE_FORMAT(DATE_ADD(DATE_SUB(created_at, INTERVAL WEEKDAY(created_at) DAY), INTERVAL 6 DAY), '%Y-%m-%d')) as week_range, " +
            "COUNT(DISTINCT CASE WHEN status = 'Accepted' THEN problem_id END) as solved_count, " +
            "COALESCE(SUM(runtime) / 3600000.0, 0) as time_spent_hours " +
            "FROM submissions " +
            "WHERE user_id = #{userId} " +
            "GROUP BY week_range " +
            "ORDER BY week_range DESC")
    List<Object[]> findWeeklyProgress(@Param("userId") String userId);
}
