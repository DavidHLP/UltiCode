package com.ulticode.modules.submission.result;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * MyBatis-Plus mapper for {@link SubmissionResultOutboxRecord} (P6-RESULT-001).
 *
 * <p>Idempotency: {@code uniq_result_sub_gen} on {@code (submission_id, generation)}
 * ensures one event per verdict generation. Each rejudge creates a new row.
 */
@Mapper
public interface SubmissionResultOutboxMapper extends BaseMapper<SubmissionResultOutboxRecord> {

    /**
     * Insert a result event row. Idempotent on (submission_id, generation):
     * if the same generation already exists, the INSERT is a no-op.
     *
     * @return 1 on insert, 0 if (submission_id, generation) already exists
     */
    @org.apache.ibatis.annotations.Insert("""
        INSERT IGNORE INTO submission_result_outbox
          (id, submission_id, generation, user_id, problem_id, verdict, runtime_ms, memory_mb, contest_id, state, attempts, next_retry_at)
        VALUES
          (#{id}, #{submissionId}, #{generation}, #{userId}, #{problemId}, #{verdict}, #{runtimeMs}, #{memoryMb}, #{contestId}, 'PENDING', 0, NOW(3))
        """)
    int insertIfAbsent(@Param("id") String id,
                       @Param("submissionId") String submissionId,
                       @Param("generation") long generation,
                       @Param("userId") String userId,
                       @Param("problemId") String problemId,
                       @Param("verdict") String verdict,
                       @Param("runtimeMs") int runtimeMs,
                       @Param("memoryMb") double memoryMb,
                       @Param("contestId") String contestId);

    @Update("""
        UPDATE submission_result_outbox SET
          state = 'CLAIMED'
        WHERE id IN (
          SELECT id FROM (
            SELECT id FROM submission_result_outbox
            WHERE state = 'PENDING' AND next_retry_at <= NOW(3)
            ORDER BY created_at
            LIMIT #{limit}
          ) AS claimable
        )
        """)
    int claimPending(@Param("limit") int limit);

    @Update("""
        UPDATE submission_result_outbox SET
          state = 'DELIVERED',
          delivered_at = NOW(3),
          last_error = NULL
        WHERE id = #{id}
        """)
    int markDelivered(@Param("id") String id);

    @Update("""
        UPDATE submission_result_outbox SET
          state = CASE WHEN attempts + 1 >= #{maxAttempts} THEN 'DEAD' ELSE 'PENDING' END,
          attempts = attempts + 1,
          last_error = #{error},
          next_retry_at = DATE_ADD(NOW(3), INTERVAL POWER(2, LEAST(attempts + 1, 10)) SECOND)
        WHERE id = #{id}
        """)
    int markFailed(@Param("id") String id, @Param("error") String error,
                   @Param("maxAttempts") int maxAttempts);
}
