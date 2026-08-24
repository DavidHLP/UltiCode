package com.ulticode.modules.submission.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ulticode.submission.api.dto.LanguageCountDTO;
import com.ulticode.submission.api.dto.LanguageStatsDTO;
import com.ulticode.submission.api.dto.MonthlySubmissionStatsDTO;
import com.ulticode.submission.api.dto.WeeklyProgressDTO;
import com.ulticode.submission.api.dto.StatusCountDTO;
import com.ulticode.submission.api.dto.UserBestStats;
import com.ulticode.common.dto.DashboardBucketCount;
import com.ulticode.modules.submission.entity.Submission;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Submission storage mapper owned by {@code backend-submission}.
 *
 * <p>SPLIT-003 slice-2 copies the write-path methods from the App-owned
 * mapper: CRUD (via {@link BaseMapper}) plus the verdict fence CAS. The
 * dashboard-only aggregate reads below are the narrow owner seam used during
 * the Admin read migration; other read contracts remain in App.
 */
@Mapper
public interface SubmissionMapper extends BaseMapper<Submission> {

    /** Select expired JUDGING rows for owner-side lease recovery. */
    @Select("SELECT * FROM submissions "
            + "WHERE status = 'Judging' AND judging_lease_expires_at IS NOT NULL "
            + "  AND judging_lease_expires_at < NOW() "
            + "ORDER BY judging_lease_expires_at "
            + "LIMIT #{batchSize} "
            + "FOR UPDATE SKIP LOCKED")
    List<Submission> selectExpiredJudgingForUpdate(@Param("batchSize") int batchSize);

    /** Bump the generation and return an expired lease to Pending atomically. */
    @Update("UPDATE submissions "
            + "SET status = 'Pending', generation = #{newGen}, "
            + "    current_attempt_id = NULL, judging_lease_expires_at = NULL "
            + "WHERE id = #{id} AND generation = #{expectedGen}")
    int bumpGenerationAndReset(@Param("id") String id,
                               @Param("expectedGen") long expectedGen,
                               @Param("newGen") long newGen);

    /**
     * CAS verdict write under generation/attempt fence. Returns 1 only when the
     * row still matches the caller's generation and attempt; otherwise the
     * judge result is stale and must be dropped.
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

    /** Read the source generation under the same row lock used by rejudge. */
    @Select("SELECT generation FROM submissions WHERE id = #{id} FOR UPDATE")
    Long findGenerationForUpdate(@Param("id") String id);

    /**
     * Atomically transition a submission from Pending to Judging and acquire a
     * lease (ADR-003 §2.3). CAS on {@code status='Pending' AND generation=#{generation}}
     * so a worker that polled a stale queue entry for a generation that has
     * since been bumped cannot grab the lease. Times use the DB clock (NOW() /
     * DATE_ADD) rather than the JVM clock (ADR-003 §1.1 F3).
     *
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
     * attempt holder; any other writer leaves {@code current_attempt_id}
     * changed, so this CAS returns 0 and the worker knows it has lost the
     * lease and must discard its in-flight verdict.
     *
     * @return 1 if renewed, 0 if the attempt no longer holds the lease
     */
    @Update("UPDATE submissions "
            + "SET judging_lease_expires_at = DATE_ADD(NOW(), INTERVAL #{ttlSeconds} SECOND) "
            + "WHERE id = #{id} AND current_attempt_id = #{attemptId}")
    int renewLease(@Param("id") String id,
                   @Param("attemptId") String attemptId,
                   @Param("ttlSeconds") long ttlSeconds);

    /** Distinct submission language codes observed in the store (admin read). */
    @Select("SELECT DISTINCT language FROM submissions ORDER BY language")
    List<String> findDistinctLanguages();

    /** Count submissions grouped by status — typed projection (admin read). */
    @Select("SELECT status, COUNT(*) AS count FROM submissions WHERE status IS NOT NULL GROUP BY status")
    List<StatusCountDTO> countByStatusTyped();

    /** Count submissions grouped by language — typed projection (admin read). */
    @Select("SELECT language, COUNT(*) AS count FROM submissions WHERE language IS NOT NULL GROUP BY language ORDER BY count DESC")
    List<LanguageCountDTO> countByLanguageTyped();

    /** Distinct submitters with at least one submission in {@code [from, to)} (admin read). */
    @Select("SELECT COUNT(DISTINCT user_id) FROM submissions "
            + "WHERE created_at >= #{startDate} AND created_at < #{endDate}")
    long countDistinctUsersInRange(@Param("startDate") LocalDateTime startDate,
                                   @Param("endDate") LocalDateTime endDate);

    /** Submission dashboard date buckets owned by the Submission schema. */
    @Select("""
            SELECT DATE_FORMAT(created_at, #{dateFormat}) AS bucket, COUNT(*) AS count
            FROM submissions
            WHERE created_at >= #{startDate} AND created_at <= #{endDate}
            GROUP BY DATE_FORMAT(created_at, #{dateFormat})
            ORDER BY bucket
            """)
    List<DashboardBucketCount> countDashboardByBucket(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("dateFormat") String dateFormat);

    /** Preserve the legacy Admin Dashboard acceptance-rate calculation. */
    @Select("SELECT COALESCE(SUM(CASE WHEN status = 'Accepted' THEN 1 ELSE 0 END) * 100.0 / NULLIF(COUNT(*), 0), 0) FROM submissions")
    Double calculateDashboardAcceptanceRate();

    /** Submission calendar dates for a user/year (user read). */
    @Select("SELECT DISTINCT DATE_FORMAT(created_at, '%Y-%m-%d') as date FROM submissions "
            + "WHERE user_id = #{userId} AND YEAR(created_at) = #{year} ORDER BY date")
    List<String> findSubmissionDatesByYear(@Param("userId") String userId,
                                           @Param("year") Integer year);

    /** Current streak in days (user read). */
    @Select("WITH RECURSIVE dates AS ( "
            + "  SELECT CURDATE() as date, 1 as day_num "
            + "  UNION ALL "
            + "  SELECT DATE_SUB(date, INTERVAL 1 DAY), day_num + 1 "
            + "  FROM dates "
            + "  WHERE day_num <= 365 "
            + "), "
            + "submission_dates AS ( "
            + "  SELECT DISTINCT DATE(created_at) as date "
            + "  FROM submissions "
            + "  WHERE user_id = #{userId} AND created_at >= DATE_SUB(CURDATE(), INTERVAL 365 DAY) "
            + "), "
            + "streak_calc AS ( "
            + "  SELECT d.date, "
            + "         ROW_NUMBER() OVER (ORDER BY d.date DESC) as rn, "
            + "         DATEDIFF(CURDATE(), d.date) as days_ago "
            + "  FROM dates d "
            + "  LEFT JOIN submission_dates sd ON d.date = sd.date "
            + "  WHERE sd.date IS NOT NULL "
            + ") "
            + "SELECT MIN(days_ago) FROM streak_calc WHERE days_ago <= 1")
    Integer calculateStreak(@Param("userId") String userId);

    /** Monthly submission stats for a user (user read). */
    @Select("SELECT DATE_FORMAT(created_at, '%Y-%m') as month, "
            + "COUNT(*) as total_count, "
            + "SUM(CASE WHEN status = 'Accepted' THEN 1 ELSE 0 END) as accepted_count "
            + "FROM submissions WHERE user_id = #{userId} "
            + "GROUP BY DATE_FORMAT(created_at, '%Y-%m') "
            + "ORDER BY month DESC")
    List<MonthlySubmissionStatsDTO> findMonthlySubmissionStats(@Param("userId") String userId);

    /** Language stats for a user (user read). */
    @Select("SELECT language, COUNT(*) as count FROM submissions "
            + "WHERE user_id = #{userId} "
            + "GROUP BY language "
            + "ORDER BY count DESC")
    List<LanguageStatsDTO> findLanguageStats(@Param("userId") String userId);

    /** Weekly progress for a user (user read). */
    @Select("SELECT CONCAT(DATE_FORMAT(DATE_SUB(created_at, INTERVAL WEEKDAY(created_at) DAY), '%Y-%m-%d'), ' to ', "
            + "DATE_FORMAT(DATE_ADD(DATE_SUB(created_at, INTERVAL WEEKDAY(created_at) DAY), INTERVAL 6 DAY), '%Y-%m-%d')) as week_range, "
            + "COUNT(DISTINCT CASE WHEN status = 'Accepted' THEN problem_id END) as solved_count, "
            + "COALESCE(SUM(runtime) / 3600000.0, 0) as time_spent_hours "
            + "FROM submissions "
            + "WHERE user_id = #{userId} "
            + "GROUP BY week_range "
            + "ORDER BY week_range DESC")
    List<WeeklyProgressDTO> findWeeklyProgress(@Param("userId") String userId);

    /**
     * Per-user best accepted runtime/memory for a problem+language — input to
     * the performance percentile computation (SPLIT-003 slice-2 copy).
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

    /**
     * Paginated submissions for a user, newest first — the read-routing
     * equivalent of App's {@code findByUserIdWithProblem} minus the
     * problems JOIN (DEC-011). Problem display facts are enriched through
     * the batch {@link ProblemFactsPort} seam instead.
     */
    @Select("SELECT * FROM submissions WHERE user_id = #{userId} "
            + "ORDER BY created_at DESC, id DESC")
    IPage<Submission> findByUserId(@Param("userId") String userId, Page<Submission> page);

    /**
     * Paginated submissions for a user and problem, newest first.
     *
     * <p>Selects only the summary columns {@code SubmissionListItemVO}
     * projects: materializing {@code code}, {@code test_details}, or the
     * distribution JSON per row would inflate DB I/O and heap on every list
     * page for data the list never reads. Because only scalar columns are
     * selected, no JSON type-handler result map is required.
     *
     * <p>{@code id} is the unique tie-breaker: {@code created_at} is
     * {@code DATETIME(3)}, so equal timestamps would otherwise reorder tied
     * rows between offset pages and duplicate or skip submissions.
     */
    @Select("SELECT id, status, language, runtime, memory, created_at, notes "
            + "FROM submissions WHERE problem_id = #{problemId} AND user_id = #{userId} "
            + "ORDER BY created_at DESC, id DESC")
    IPage<Submission> findByProblemId(@Param("problemId") Long problemId,
                                      @Param("userId") String userId,
                                      Page<Submission> page);

    /**
     * Best (fastest accepted) submission for a problem+user. Pure
     * submissions-table read, identical SQL to App (no JOIN).
     */
    @Select("SELECT * FROM submissions WHERE problem_id = #{problemId} AND user_id = #{userId} " +
            "AND status = 'Accepted' ORDER BY runtime ASC, memory ASC, created_at DESC LIMIT 1")
    Optional<Submission> findBestByProblemIdAndUserId(@Param("problemId") Long problemId,
                                                       @Param("userId") String userId);
}
