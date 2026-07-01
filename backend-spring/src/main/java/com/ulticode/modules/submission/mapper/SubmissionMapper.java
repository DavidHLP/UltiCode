package com.ulticode.modules.submission.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ulticode.modules.submission.dto.LanguageCountDTO;
import com.ulticode.modules.submission.dto.LanguageStatsDTO;
import com.ulticode.modules.submission.dto.StatusCountDTO;
import com.ulticode.modules.submission.dto.MonthlySubmissionStatsDTO;
import com.ulticode.modules.submission.dto.SubmissionDateCountDTO;
import com.ulticode.modules.submission.dto.UserBestStats;
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
import org.apache.ibatis.annotations.Update;

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
     * Count submissions by user and language.
     *
     * @param userId user ID
     * @param language the programming language
     * @return count of submissions in the given language
     */
    @Select("SELECT COUNT(*) FROM submissions WHERE user_id = #{userId} AND language = #{language}")
    Long countByUserIdAndLanguage(@Param("userId") String userId, @Param("language") String language);

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
     * @return list of maps containing [status, count]
     */
    @Select("SELECT status, COUNT(*) as count FROM submissions WHERE status IS NOT NULL GROUP BY status")
    List<Map<String, Object>> countByStatus();

    /**
     * Count submissions grouped by language (for admin statistics).
     *
     * @return list of maps containing [language, count]
     */
    @Select("SELECT language, COUNT(*) as count FROM submissions WHERE language IS NOT NULL GROUP BY language ORDER BY count DESC")
    List<Map<String, Object>> countByLanguage();

    /**
     * Find distinct languages used in submissions.
     *
     * @return list of distinct language strings
     */
    @Select("SELECT DISTINCT language FROM submissions ORDER BY language")
    List<String> findDistinctLanguages();

    /**
     * Count submissions grouped by status — typed projection. Replaces the
     * previous {@code Map<String,Object>} return so callers cannot mistype
     * a key and produce a silent runtime bug.
     */
    @Select("SELECT status, COUNT(*) AS count FROM submissions WHERE status IS NOT NULL GROUP BY status")
    List<StatusCountDTO> countByStatusTyped();

    /**
     * Count submissions grouped by language — typed projection.
     */
    @Select("SELECT language, COUNT(*) AS count FROM submissions WHERE language IS NOT NULL GROUP BY language ORDER BY count DESC")
    List<LanguageCountDTO> countByLanguageTyped();

    /**
     * Per-user best (MIN runtime, MIN memory) among accepted submissions for
     * a given problem/language pair, aggregated in SQL rather than loading
     * every accepted row.
     *
     * <p>Used by {@code SubmissionServiceImpl.applyPerformanceStats} to
     * compute percentile / distribution bins for an accepted submission
     * without scanning the full accepted-submission history on each read.
     * The result is bounded by the number of distinct users who solved the
     * problem, not by total submission count.
     *
     * <p>Rows with NULL user_id are excluded (defensive — should not occur
     * given the schema but the join guarantees the aggregate does not
     * silently absorb them into a single NULL-keyed bucket).
     *
     * @param problemId problem id to scope the aggregate to
     * @param language  language to scope the aggregate to
     * @return list of per-user best stats, never null (empty when no
     *         accepted submissions exist)
     */
    @ConstructorArgs({
            @Arg(column = "user_id", javaType = String.class),
            @Arg(column = "best_runtime_ms", javaType = Integer.class),
            @Arg(column = "best_memory_mb", javaType = Double.class)
    })
    @Select("SELECT user_id, "
            + "MIN(runtime) AS best_runtime_ms, "
            + "MIN(memory) AS best_memory_mb "
            + "FROM submissions "
            + "WHERE problem_id = #{problemId} "
            + "  AND language = #{language} "
            + "  AND status = 'Accepted' "
            + "  AND user_id IS NOT NULL "
            + "GROUP BY user_id")
    List<UserBestStats> findBestStatsByProblemAndLanguage(
            @Param("problemId") Long problemId,
            @Param("language") String language);

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
    @Select("SELECT UPPER(p.difficulty) as difficulty, "
            + "COUNT(DISTINCT p.id) as total_problems, "
            + "COUNT(DISTINCT CASE WHEN s.status = 'Accepted' THEN p.id END) as solved_problems "
            + "FROM problems p "
            + "LEFT JOIN submissions s ON s.problem_id = p.id AND s.status = 'Accepted' "
            + "WHERE p.is_deleted = false AND p.difficulty IS NOT NULL "
            + "GROUP BY UPPER(p.difficulty)")
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
     * Daily active users aggregation based on submissions.
     * Groups submissions by date and counts distinct users per day.
     * This provides a more accurate DAU metric than audit_logs
     * which only records admin operations.
     *
     * @param startDate start of analysis period (inclusive)
     * @param endDate   end of analysis period (exclusive)
     * @return list of rows with date, count
     */
    @Select("SELECT DATE(created_at) AS date, COUNT(DISTINCT user_id) AS count "
            + "FROM submissions "
            + "WHERE created_at >= #{startDate} AND created_at < #{endDate} "
            + "GROUP BY DATE(created_at) ORDER BY date")
    List<Map<String, Object>> countDailyActiveUsers(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

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

    // ==================== ADR-003 M3b: Generation Fence + Lease CAS ====================
    //
    // All four updates below are single-statement atomic CAS operations. They
    // return the number of affected rows so callers can detect fence mismatches
    // (affected = 0) and act accordingly:
    //   - acquireLease / renewLease affected = 0  -> lease lost, give up
    //   - writeVerdictFenced affected = 0         -> stale result, drop + metric
    //   - bumpGenerationAndReset affected = 0     -> concurrent bump, skip
    //
    // Times use the DB clock (NOW() / DATE_ADD) rather than the JVM clock to
    // stay correct under container clock drift (ADR-003 §1.1 F3 sub-issue 5).

    /**
     * Atomically transition a submission from Pending to Judging and acquire a
     * lease (ADR-003 §2.3). CAS on {@code status='Pending' AND generation=#{generation}}
     * so a worker that polled a stale queue entry for a generation that has
     * since been bumped cannot grab the lease. Sets {@code current_attempt_id}
     * to the worker's attempt UUID and arms {@code judging_lease_expires_at}.
     *
     * @param id           submission id
     * @param attemptId    worker attempt UUID (this attempt's fence identity)
     * @param generation   generation the worker observed when it polled
     * @param ttlSeconds   lease TTL in seconds
     * @return 1 if the lease was acquired, 0 otherwise (already judging or gen mismatch)
     */
    @Update("UPDATE submissions "
            + "SET status = 'Judging', current_attempt_id = #{attemptId}, "
            + "    judging_lease_expires_at = DATE_ADD(NOW(), INTERVAL #{ttlSeconds} SECOND) "
            + "WHERE id = #{id} AND status = 'Pending' AND generation = #{generation}")
    int acquireLease(@Param("id") String id,
                     @Param("attemptId") String attemptId,
                     @Param("generation") long generation,
                     @Param("ttlSeconds") long ttlSeconds);

    /**
     * Heartbeat renewal (ADR-003 §2.3). Extends the lease only for the current
     * attempt holder; any other writer (reaper, a re-acquired attempt) leaves
     * {@code current_attempt_id} changed, so this CAS returns 0 and the worker
     * knows it has lost the lease and must discard its in-flight verdict.
     *
     * @param id          submission id
     * @param attemptId   worker attempt UUID (must still match)
     * @param ttlSeconds  lease TTL in seconds
     * @return 1 if renewed, 0 if the attempt no longer holds the lease
     */
    @Update("UPDATE submissions "
            + "SET judging_lease_expires_at = DATE_ADD(NOW(), INTERVAL #{ttlSeconds} SECOND) "
            + "WHERE id = #{id} AND current_attempt_id = #{attemptId}")
    int renewLease(@Param("id") String id,
                   @Param("attemptId") String attemptId,
                   @Param("ttlSeconds") long ttlSeconds);

    /**
     * Write a terminal verdict behind the generation+attempt fence (ADR-003
     * §2.2). Clears the lease fields so the row leaves JUDGING. affected = 0
     * means the generation was bumped (rejudge / reaper) or the attempt lost
     * the lease after acquisition — the caller must drop the result and increment
     * the {@code judge.stale_result.dropped} metric.
     *
     * <p>{@code runtime}/{@code memory}/{@code testDetailsJson} are nullable for
     * the System-Error-no-details path; MyBatis renders NULL for null params.
     *
     * @param id                submission id
     * @param generation        generation the worker observed
     * @param attemptId         attempt UUID the worker holds
     * @param status            terminal status wire value
     * @param runtime           runtime in ms (may be null)
     * @param memory            memory in MB (may be null)
     * @param testDetailsJson   serialized test details JSON (may be null)
     * @return 1 if written, 0 if the fence rejected the write
     */
    @Update("UPDATE submissions "
            + "SET status = #{status}, runtime = #{runtime}, memory = #{memory}, "
            + "    test_details = #{testDetailsJson}, "
            + "    current_attempt_id = NULL, judging_lease_expires_at = NULL "
            + "WHERE id = #{id} AND generation = #{generation} AND current_attempt_id = #{attemptId}")
    int writeVerdictFenced(@Param("id") String id,
                           @Param("generation") long generation,
                           @Param("attemptId") String attemptId,
                           @Param("status") String status,
                           @Param("runtime") Integer runtime,
                           @Param("memory") Double memory,
                           @Param("testDetailsJson") String testDetailsJson);

    /**
     * Write a terminal verdict behind the generation+attempt fence, atomically
     * persisting the computed performance stats (percentile + distribution bins)
     * in the SAME CAS (ADR-003 M3b, F4 fix).
     *
     * <p><b>Why this overload exists (F4):</b> the original two-step path wrote
     * the verdict via the 7-arg {@link #writeVerdictFenced} CAS, then ran a
     * separate full-entity {@code submissionMapper.updateById(submission)} to
     * persist the percentile/bin columns computed by
     * {@code computePerformanceStats}. That second update is <b>unfenced</b>:
     * between the CAS commit and the {@code updateById}, an admin rejudge could
     * bump the generation, and the unfenced update would write the stale
     * Accepted status + old generation + cleared lease fields back over the
     * rejudge — silently defeating the fence exactly when
     * {@code computePerformanceStats} is slow. Folding the performance columns
     * into the verdict CAS eliminates the second write entirely: all six
     * data columns land (or are rejected) behind the same generation+attempt
     * fence.
     *
     * <p>The performance params are nullable: non-Accepted verdicts and the
     * System-Error path pass nulls so the columns are cleared, matching the
     * legacy {@code updateSubmissionResult} behavior of always setting the
     * field. MyBatis renders NULL for null params.
     *
     * @param id                   submission id
     * @param generation           generation the worker observed
     * @param attemptId            attempt UUID the worker holds
     * @param status               terminal status wire value
     * @param runtime              runtime in ms (may be null)
     * @param memory               memory in MB (may be null)
     * @param testDetailsJson      serialized test details JSON (may be null)
     * @param runtimePercentile    computed runtime percentile (may be null)
     * @param memoryPercentile     computed memory percentile (may be null)
     * @param runtimeDistBinsJson  serialized runtime distribution bins JSON (may be null)
     * @param memoryDistBinsJson   serialized memory distribution bins JSON (may be null)
     * @return 1 if written, 0 if the fence rejected the write
     */
    @Update("UPDATE submissions "
            + "SET status = #{status}, runtime = #{runtime}, memory = #{memory}, "
            + "    test_details = #{testDetailsJson}, "
            + "    runtime_percentile = #{runtimePercentile}, "
            + "    memory_percentile = #{memoryPercentile}, "
            + "    runtimeDistBinsMs = #{runtimeDistBinsJson}, "
            + "    memoryDistBinsMb = #{memoryDistBinsJson}, "
            + "    current_attempt_id = NULL, judging_lease_expires_at = NULL "
            + "WHERE id = #{id} AND generation = #{generation} AND current_attempt_id = #{attemptId}")
    int writeVerdictFencedWithStats(@Param("id") String id,
                                    @Param("generation") long generation,
                                    @Param("attemptId") String attemptId,
                                    @Param("status") String status,
                                    @Param("runtime") Integer runtime,
                                    @Param("memory") Double memory,
                                    @Param("testDetailsJson") String testDetailsJson,
                                    @Param("runtimePercentile") Double runtimePercentile,
                                    @Param("memoryPercentile") Double memoryPercentile,
                                    @Param("runtimeDistBinsJson") String runtimeDistBinsJson,
                                    @Param("memoryDistBinsJson") String memoryDistBinsJson);

    /**
     * Bump generation and reset a submission back to Pending (ADR-003 §2.2).
     * Used by the lease reaper (single transaction, F7) and by admin rejudge on
     * non-JUDGING rows. CAS on {@code generation = #{expectedGen}} so a
     * concurrent reaper / rejudge cannot double-bump.
     *
     * @param id          submission id
     * @param expectedGen generation the caller observed
     * @param newGen      target generation (expectedGen + 1)
     * @return 1 if bumped, 0 if generation already moved
     */
    @Update("UPDATE submissions "
            + "SET status = 'Pending', generation = #{newGen}, "
            + "    current_attempt_id = NULL, judging_lease_expires_at = NULL "
            + "WHERE id = #{id} AND generation = #{expectedGen}")
    int bumpGenerationAndReset(@Param("id") String id,
                               @Param("expectedGen") long expectedGen,
                               @Param("newGen") long newGen);

    /**
     * Force the current JUDGING lease to expire immediately AND revoke the active
     * attempt, without bumping the generation (ADR-003 §3.3, ADR-005
     * rejudge-on-JUDGING path; F2 fix). Used by admin rejudge when the target is
     * currently JUDGING: rather than racing the worker to bump generation, the
     * caller forces lease expiry + attempt revocation and lets the reaper perform
     * the atomic bump in its single transaction (F7).
     *
     * <p><b>Why current_attempt_id is NULLed (F2):</b> the original SQL only
     * stamped {@code judging_lease_expires_at = NOW()-1s} but left
     * {@code current_attempt_id} intact. In the up-to-5s window before the
     * reaper swept the row, the still-running worker holding that attempt could
     * {@link #renewLease} (CAS keyed only on {@code current_attempt_id}) or land
     * a {@link #writeVerdictFenced} — both succeed because the attempt id still
     * matches — silently overwriting the requested rejudge. NULLing the attempt
     * id in this same CAS makes the worker's very next {@code renewLease} /
     * {@code writeVerdictFenced} fail immediately (their
     * {@code WHERE current_attempt_id = #{attemptId}} clause no longer matches),
     * so the rejudge cannot be lost before the reaper bumps the generation. CAS
     * on {@code current_attempt_id = #{attemptId}} so a rejudge that targets an
     * already-recovered row (attempt already cleared by a prior reaper bump) is
     * a no-op.
     *
     * @param id          submission id
     * @param attemptId   attempt UUID currently holding the lease; the caller must
     *                    pass the loaded submission's {@code currentAttemptId}.
     *                    NULL renders the {@code = #{attemptId}} clause unsatisfiable,
     *                    so callers guard with a non-null check (matches
     *                    {@code rejudgeFenced}'s {@code if (currentAttemptId != null)}).
     * @return 1 if the lease was forced to expire and the attempt revoked, 0 otherwise
     */
    @Update("UPDATE submissions "
            + "SET judging_lease_expires_at = DATE_SUB(NOW(), INTERVAL 1 SECOND), "
            + "    current_attempt_id = NULL "
            + "WHERE id = #{id} AND status = 'Judging' AND current_attempt_id IS NOT NULL "
            + "  AND current_attempt_id = #{attemptId}")
    int forceLeaseExpiry(@Param("id") String id,
                         @Param("attemptId") String attemptId);

    /**
     * Increment {@code retry_count} on a submission without touching any other
     * column (ADR-003 M3b, C1 fix). Used by the JUDGING branch of the fenced
     * rejudge: that branch must <b>not</b> call {@code updateById} because
     * MyBatis-Plus's default {@code NOT_NULL} update strategy would write the
     * entity's stale (pre-{@code forceLeaseExpiry}) in-memory
     * {@code judging_lease_expires_at} back to the DB, silently restoring the
     * lease the reaper needs to expire. This targeted update leaves the lease
     * columns at whatever {@code forceLeaseExpiry} just wrote, so the reaper can
     * still observe the forced expiry and perform the atomic generation bump.
     *
     * <p>Rule 05-(8): update only the column that changed.
     *
     * @param id        submission id
     * @param increment amount to add to {@code retry_count} (typically 1)
     * @return affected rows (1 on success)
     */
    @Update("UPDATE submissions SET retry_count = retry_count + #{increment} WHERE id = #{id}")
    int bumpRetryCount(@Param("id") String id, @Param("increment") int increment);

    /**
     * Select expired JUDGING rows for recovery, locking them with
     * {@code FOR UPDATE SKIP LOCKED} so multiple reaper instances (or a reaper
     * racing a rejudge) never grab the same row (ADR-003 §2.3, F7). MySQL 8.0+
     * supports SKIP LOCKED; the project targets 9.1.
     *
     * <p>Returns full Submission entities so the reaper has the observed
     * generation in hand for the bump CAS.
     *
     * @param batchSize max rows to recover in one sweep
     * @return list of expired judging submissions (empty when none)
     */
    @Select("SELECT * FROM submissions "
            + "WHERE status = 'Judging' AND judging_lease_expires_at IS NOT NULL "
            + "  AND judging_lease_expires_at < NOW() "
            + "ORDER BY judging_lease_expires_at "
            + "LIMIT #{batchSize} "
            + "FOR UPDATE SKIP LOCKED")
    List<Submission> selectExpiredJudgingForUpdate(@Param("batchSize") int batchSize);

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
    @ConstructorArgs({
            @Arg(column = "id", javaType = String.class),
            @Arg(column = "problem_id", javaType = Long.class),
            @Arg(column = "user_id", javaType = String.class),
            @Arg(column = "language", javaType = String.class),
            @Arg(column = "code", javaType = String.class),
            @Arg(column = "status", javaType = String.class),
            @Arg(column = "runtime", javaType = Integer.class),
            @Arg(column = "memory", javaType = Double.class),
            @Arg(column = "notes", javaType = String.class),
            @Arg(column = "retry_count", javaType = Integer.class),
            @Arg(column = "created_at", javaType = LocalDateTime.class),
            @Arg(column = "runtime_percentile", javaType = Double.class),
            @Arg(column = "memory_percentile", javaType = Double.class),
            @Arg(column = "test_details", javaType = Object.class),
            @Arg(column = "memoryDistBinsMb", javaType = Object.class),
            @Arg(column = "runtimeDistBinsMs", javaType = Object.class),
            @Arg(column = "title", javaType = String.class),
            @Arg(column = "slug", javaType = String.class)
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
    @ConstructorArgs({
            @Arg(column = "id", javaType = String.class),
            @Arg(column = "problem_id", javaType = Long.class),
            @Arg(column = "user_id", javaType = String.class),
            @Arg(column = "language", javaType = String.class),
            @Arg(column = "code", javaType = String.class),
            @Arg(column = "status", javaType = String.class),
            @Arg(column = "runtime", javaType = Integer.class),
            @Arg(column = "memory", javaType = Double.class),
            @Arg(column = "notes", javaType = String.class),
            @Arg(column = "retry_count", javaType = Integer.class),
            @Arg(column = "created_at", javaType = LocalDateTime.class),
            @Arg(column = "runtime_percentile", javaType = Double.class),
            @Arg(column = "memory_percentile", javaType = Double.class),
            @Arg(column = "test_details", javaType = Object.class),
            @Arg(column = "memoryDistBinsMb", javaType = Object.class),
            @Arg(column = "runtimeDistBinsMs", javaType = Object.class),
            @Arg(column = "title", javaType = String.class),
            @Arg(column = "slug", javaType = String.class)
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
