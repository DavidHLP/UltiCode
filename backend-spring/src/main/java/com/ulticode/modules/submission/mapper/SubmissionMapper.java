package com.ulticode.modules.submission.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ulticode.modules.submission.dto.LanguageStatsDTO;
import com.ulticode.modules.submission.dto.MonthlySubmissionStatsDTO;
import com.ulticode.modules.submission.dto.SubmissionDateCountDTO;
import com.ulticode.modules.submission.dto.WeeklyProgressDTO;
import com.ulticode.modules.submission.entity.Submission;
import com.ulticode.modules.user.dto.DifficultyCountDTO;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
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
    @ConstructorArgs({
            @Arg(column = "difficulty", javaType = String.class),
            @Arg(column = "count", javaType = Long.class)
    })
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
    @Results({
            @Result(property = "date", column = "date"),
            @Result(property = "count", column = "count")
    })
    @Select("SELECT DATE_FORMAT(created_at, '%Y-%m-%d') as date, COUNT(*) as count " +
            "FROM submissions " +
            "WHERE user_id = #{userId} AND YEAR(created_at) = #{year} " +
            "GROUP BY DATE_FORMAT(created_at, '%Y-%m-%d') " +
            "ORDER BY date")
    List<SubmissionDateCountDTO> findSubmissionCountsByDate(@Param("userId") String userId, @Param("year") Integer year);

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
     * @return list of MonthlySubmissionStatsDTO containing [month, totalCount, acceptedCount]
     */
    @Results({
            @Result(property = "month", column = "month"),
            @Result(property = "totalCount", column = "total_count"),
            @Result(property = "acceptedCount", column = "accepted_count")
    })
    @Select("SELECT DATE_FORMAT(created_at, '%Y-%m') as month, " +
            "COUNT(*) as total_count, " +
            "SUM(CASE WHEN status = 'Accepted' THEN 1 ELSE 0 END) as accepted_count " +
            "FROM submissions WHERE user_id = #{userId} " +
            "GROUP BY DATE_FORMAT(created_at, '%Y-%m') " +
            "ORDER BY month DESC")
    List<MonthlySubmissionStatsDTO> findMonthlySubmissionStats(@Param("userId") String userId);

    /**
     * Find submission statistics by programming language for a user.
     *
     * @param userId user ID
     * @return list of LanguageStatsDTO containing [language, count]
     */
    @Results({
            @Result(property = "language", column = "language"),
            @Result(property = "count", column = "count")
    })
    @Select("SELECT language, COUNT(*) as count FROM submissions " +
            "WHERE user_id = #{userId} " +
            "GROUP BY language " +
            "ORDER BY count DESC")
    List<LanguageStatsDTO> findLanguageStats(@Param("userId") String userId);

    /**
     * Find weekly progress (solved problems per week) for a user.
     *
     * @param userId user ID
     * @return list of WeeklyProgressDTO containing [weekRange, solvedCount, timeSpentHours]
     */
    @Results({
            @Result(property = "weekRange", column = "week_range"),
            @Result(property = "solvedCount", column = "solved_count"),
            @Result(property = "timeSpentHours", column = "time_spent_hours")
    })
    @Select("SELECT CONCAT(DATE_FORMAT(DATE_SUB(created_at, INTERVAL WEEKDAY(created_at) DAY), '%Y-%m-%d'), ' to ', " +
            "DATE_FORMAT(DATE_ADD(DATE_SUB(created_at, INTERVAL WEEKDAY(created_at) DAY), INTERVAL 6 DAY), '%Y-%m-%d')) as week_range, " +
            "COUNT(DISTINCT CASE WHEN status = 'Accepted' THEN problem_id END) as solved_count, " +
            "COALESCE(SUM(runtime) / 3600000.0, 0) as time_spent_hours " +
            "FROM submissions " +
            "WHERE user_id = #{userId} " +
            "GROUP BY week_range " +
            "ORDER BY week_range DESC")
    List<WeeklyProgressDTO> findWeeklyProgress(@Param("userId") String userId);

    /**
     * Count submissions grouped by status (for admin statistics).
     *
     * @return list of Object arrays containing [status, count]
     */
    @Select("SELECT status, COUNT(*) as count FROM submissions GROUP BY status")
    List<Object[]> countByStatus();

    /**
     * Count submissions grouped by language (for admin statistics).
     *
     * @return list of Object arrays containing [language, count]
     */
    @Select("SELECT language, COUNT(*) as count FROM submissions GROUP BY language ORDER BY count DESC")
    List<Object[]> countByLanguage();

    /**
     * Find distinct languages used in submissions.
     *
     * @return list of distinct language strings
     */
    @Select("SELECT DISTINCT language FROM submissions ORDER BY language")
    List<String> findDistinctLanguages();

    // ==================== Admin Analytics Aggregation Methods ====================

    /**
     * Weekly active users aggregation (replaces N+1 per-week loop).
     * Groups submissions by ISO week and counts distinct users per week.
     *
     * @param startDate start of analysis period
     * @return list of rows with yearweek, week_start, count
     */
    @Select("SELECT YEARWEEK(created_at, 3) as yearweek, "
            + "ANY_VALUE(DATE(DATE_SUB(created_at, INTERVAL WEEKDAY(created_at) DAY))) as week_start, "
            + "COUNT(DISTINCT user_id) as count "
            + "FROM submissions "
            + "WHERE created_at >= #{startDate} "
            + "GROUP BY YEARWEEK(created_at, 3) "
            + "ORDER BY yearweek")
    List<Map<String, Object>> countWeeklyActiveUsers(@Param("startDate") LocalDateTime startDate);

    /**
     * Peak active hours aggregation (replaces 24 individual COUNT queries).
     * Groups submissions by hour of day and counts distinct users.
     *
     * @param startDate start of analysis period
     * @return list of rows with hour, count
     */
    @Select("SELECT HOUR(created_at) as hour, COUNT(DISTINCT user_id) as count "
            + "FROM submissions "
            + "WHERE created_at >= #{startDate} "
            + "GROUP BY HOUR(created_at) "
            + "ORDER BY hour")
    List<Map<String, Object>> countActiveUsersByHour(@Param("startDate") LocalDateTime startDate);

    /**
     * Top active users by submission count (replaces load-all + Java groupBy + N user lookups).
     * Groups submissions by user_id and returns top N submitters.
     *
     * @param startDate start of analysis period
     * @param limit     max number of users to return
     * @return list of rows with user_id, submission_count
     */
    @Select("SELECT user_id, COUNT(*) as submission_count "
            + "FROM submissions "
            + "WHERE created_at >= #{startDate} "
            + "GROUP BY user_id "
            + "ORDER BY submission_count DESC "
            + "LIMIT #{limit}")
    List<Map<String, Object>> findTopActiveUsers(
            @Param("startDate") LocalDateTime startDate,
            @Param("limit") int limit);

    /**
     * Problem completion by difficulty (replaces N+1 per-problem per-difficulty loop).
     * Joins problems with accepted submissions and aggregates by difficulty level.
     *
     * @return list of rows with difficulty, total_problems, solved_problems
     */
    @Select("SELECT p.difficulty, "
            + "COUNT(DISTINCT p.id) as total_problems, "
            + "COUNT(DISTINCT CASE WHEN s.status = 'Accepted' THEN p.id END) as solved_problems "
            + "FROM problems p "
            + "LEFT JOIN submissions s ON s.problem_id = p.id AND s.status = 'Accepted' "
            + "WHERE p.status = 'PUBLISHED' AND p.difficulty IS NOT NULL "
            + "GROUP BY p.difficulty")
    List<Map<String, Object>> countProblemCompletionByDifficulty();

    /**
     * Trending problems (replaces load-all + Java groupBy + N problem lookups).
     * Groups submissions by problem_id and returns top N most attempted problems
     * with their acceptance counts.
     *
     * @param startDate start of analysis period
     * @param limit     max number of problems to return
     * @return list of rows with problem_id, attempt_count, accepted_count
     */
    @Select("SELECT problem_id, COUNT(*) as attempt_count, "
            + "SUM(CASE WHEN status = 'Accepted' THEN 1 ELSE 0 END) as accepted_count "
            + "FROM submissions "
            + "WHERE created_at >= #{startDate} "
            + "GROUP BY problem_id "
            + "ORDER BY attempt_count DESC "
            + "LIMIT #{limit}")
    List<Map<String, Object>> findTrendingProblems(
            @Param("startDate") LocalDateTime startDate,
            @Param("limit") int limit);

    /**
     * Retention rate helper: count distinct users in a date range.
     * Replaces buggy selectCount with groupBy (returns count of first group,
     * not total distinct users) with proper COUNT(DISTINCT user_id).
     *
     * @param startDate range start (inclusive)
     * @param endDate   range end (exclusive)
     * @return count of distinct users who submitted in the range
     */
    @Select("SELECT COUNT(DISTINCT user_id) FROM submissions "
            + "WHERE created_at >= #{startDate} AND created_at < #{endDate}")
    long countDistinctUsersInRange(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    /**
     * Contest participation aggregation (replaces N+1 per-contest participant loading).
     * NOTE: Uses ${contestIds} (string interpolation) because MyBatis #{} cannot
     * expand comma-separated IN-lists. The calling code MUST construct the contestIds
     * string safely from validated Long IDs to prevent SQL injection. Only pass numeric IDs.
     *
     * @param contestIds comma-separated contest IDs (e.g., "1,2,3")
     * @return list of rows with contest_id, participant_count
     */
    @Select("SELECT contest_id, COUNT(DISTINCT user_id) as participant_count "
            + "FROM contest_participants "
            + "WHERE contest_id IN (${contestIds}) "
            + "GROUP BY contest_id")
    List<Map<String, Object>> countParticipantsByContest(@Param("contestIds") String contestIds);

    /**
     * Get global rank for a user based on accepted submission count.
     * Rank = number of users with more accepted submissions + 1.
     * Returns null if user has no accepted submissions.
     *
     * @param userId user ID
     * @return the global rank based on AC count, or null if user has no accepted submissions
     */
    @Select("SELECT COUNT(*) + 1 FROM submissions s1 " +
            "WHERE s1.user_id != #{userId} " +
            "AND s1.status = 'Accepted' " +
            "AND (SELECT COUNT(*) FROM submissions s2 WHERE s2.user_id = #{userId} AND s2.status = 'Accepted') < " +
            "(SELECT COUNT(*) FROM submissions s3 WHERE s3.user_id = s1.user_id AND s3.status = 'Accepted')")
    Integer findGlobalRankByUserId(@Param("userId") String userId);

    /**
     * Calculate acceptance rate for a user (percentage of accepted submissions).
     *
     * @param userId user ID
     * @return the acceptance rate as a percentage (0-100), or null if no submissions
     */
    @Select("SELECT " +
            "SUM(CASE WHEN status = 'Accepted' THEN 1 ELSE 0 END) * 100.0 / NULLIF(COUNT(*), 0) " +
            "FROM submissions WHERE user_id = #{userId}")
    Double calculateAcceptanceRateByUserId(@Param("userId") String userId);

    /**
     * Count total submissions for a user.
     *
     * @param userId user ID
     * @return the total number of submissions
     */
    @Select("SELECT COUNT(*) FROM submissions WHERE user_id = #{userId}")
    Long countTotalSubmissionsByUserId(@Param("userId") String userId);

    /**
     * DTO record holding Submission fields plus joined problem data.
     */
    record SubmissionWithProblem(
            String id,
            Long problemId,
            String userId,
            String language,
            String code,
            String status,
            Integer runtime,
            Double memory,
            String notes,
            Integer retryCount,
            LocalDateTime createdAt,
            Double runtimePercentile,
            Double memoryPercentile,
            Object testDetails,
            Object memoryDistBinsMb,
            Object runtimeDistBinsMs,
            String problemTitle,
            String problemSlug
    ) {}

    /**
     * Find submissions by user ID with problem data joined.
     * Eliminates N+1 problem lookups in list views.
     *
     * @param userId user ID
     * @param page   pagination object
     * @return paginated submissions with problem title/slug
     */
    @Results({
            @Result(column = "id", property = "id"),
            @Result(column = "problem_id", property = "problemId"),
            @Result(column = "user_id", property = "userId"),
            @Result(column = "language", property = "language"),
            @Result(column = "code", property = "code"),
            @Result(column = "status", property = "status"),
            @Result(column = "runtime", property = "runtime"),
            @Result(column = "memory", property = "memory"),
            @Result(column = "notes", property = "notes"),
            @Result(column = "retry_count", property = "retryCount"),
            @Result(column = "created_at", property = "createdAt"),
            @Result(column = "runtime_percentile", property = "runtimePercentile"),
            @Result(column = "memory_percentile", property = "memoryPercentile"),
            @Result(column = "test_details", property = "testDetails"),
            @Result(column = "memory_dist_bins_mb", property = "memoryDistBinsMb"),
            @Result(column = "runtime_dist_bins_ms", property = "runtimeDistBinsMs"),
            @Result(column = "title", property = "problemTitle"),
            @Result(column = "slug", property = "problemSlug")
    })
    @Select("SELECT s.*, p.title, p.slug " +
            "FROM submissions s " +
            "LEFT JOIN problems p ON s.problem_id = p.id " +
            "WHERE s.user_id = #{userId} " +
            "ORDER BY s.created_at DESC")
    IPage<SubmissionWithProblem> findByUserIdWithProblem(@Param("userId") String userId, Page<Submission> page);

    /**
     * Find submissions by problem ID with problem data joined.
     * Eliminates N+1 problem lookups in list views.
     *
     * @param problemId problem ID
     * @param userId   user ID (optional filter)
     * @param page     pagination object
     * @return paginated submissions with problem title/slug
     */
    @Results({
            @Result(column = "id", property = "id"),
            @Result(column = "problem_id", property = "problemId"),
            @Result(column = "user_id", property = "userId"),
            @Result(column = "language", property = "language"),
            @Result(column = "code", property = "code"),
            @Result(column = "status", property = "status"),
            @Result(column = "runtime", property = "runtime"),
            @Result(column = "memory", property = "memory"),
            @Result(column = "notes", property = "notes"),
            @Result(column = "retry_count", property = "retryCount"),
            @Result(column = "created_at", property = "createdAt"),
            @Result(column = "runtime_percentile", property = "runtimePercentile"),
            @Result(column = "memory_percentile", property = "memoryPercentile"),
            @Result(column = "test_details", property = "testDetails"),
            @Result(column = "memory_dist_bins_mb", property = "memoryDistBinsMb"),
            @Result(column = "runtime_dist_bins_ms", property = "runtimeDistBinsMs"),
            @Result(column = "title", property = "problemTitle"),
            @Result(column = "slug", property = "problemSlug")
    })
    @Select("<script>" +
            "SELECT s.*, p.title, p.slug " +
            "FROM submissions s " +
            "LEFT JOIN problems p ON s.problem_id = p.id " +
            "WHERE s.problem_id = #{problemId} " +
            "<if test='userId != null'> AND s.user_id = #{userId}</if>" +
            " ORDER BY s.created_at DESC" +
            "</script>")
    IPage<SubmissionWithProblem> findByProblemIdWithProblem(
            @Param("problemId") Long problemId,
            @Param("userId") String userId,
            Page<Submission> page);
}
