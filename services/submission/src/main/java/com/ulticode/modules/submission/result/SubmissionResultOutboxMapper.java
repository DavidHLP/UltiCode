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
     * a stale non-terminal row is replaced by the terminal verdict, while an
     * existing terminal row remains unchanged.
     *
     * @return 1 on insert or replacement, 0 if a terminal row already exists
     */
    @org.apache.ibatis.annotations.Insert("""
        INSERT INTO submission_result_outbox
          (id, submission_id, generation, user_id, problem_id, verdict, runtime_ms, memory_mb, contest_id, state, attempts, next_retry_at)
        VALUES
          (#{id}, #{submissionId}, #{generation}, #{userId}, #{problemId}, #{verdict}, #{runtimeMs}, #{memoryMb}, #{contestId}, 'PENDING', 0, NOW(3))
        ON DUPLICATE KEY UPDATE
          user_id = IF(verdict IN ('Pending', 'Judging'), VALUES(user_id), user_id),
          problem_id = IF(verdict IN ('Pending', 'Judging'), VALUES(problem_id), problem_id),
          runtime_ms = IF(verdict IN ('Pending', 'Judging'), VALUES(runtime_ms), runtime_ms),
          memory_mb = IF(verdict IN ('Pending', 'Judging'), VALUES(memory_mb), memory_mb),
          contest_id = IF(verdict IN ('Pending', 'Judging'), VALUES(contest_id), contest_id),
          state = IF(verdict IN ('Pending', 'Judging'), 'PENDING', state),
          attempts = IF(verdict IN ('Pending', 'Judging'), 0, attempts),
          last_error = IF(verdict IN ('Pending', 'Judging'), NULL, last_error),
          claimed_at = IF(verdict IN ('Pending', 'Judging'), NULL, claimed_at),
          claim_owner = IF(verdict IN ('Pending', 'Judging'), NULL, claim_owner),
          delivered_at = IF(verdict IN ('Pending', 'Judging'), NULL, delivered_at),
          next_retry_at = IF(verdict IN ('Pending', 'Judging'), NOW(3), next_retry_at),
          verdict = IF(verdict IN ('Pending', 'Judging'), VALUES(verdict), verdict)
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
          state = 'CLAIMED',
          claimed_at = NOW(3),
          claim_owner = #{claimOwner}
        WHERE state = 'PENDING'
          AND verdict NOT IN ('Pending', 'Judging')
          AND next_retry_at <= NOW(3)
          AND id IN (
            SELECT id FROM (
              SELECT id FROM submission_result_outbox
              WHERE state = 'PENDING'
                AND verdict NOT IN ('Pending', 'Judging')
                AND next_retry_at <= NOW(3)
              ORDER BY created_at
              LIMIT #{limit}
            ) AS claimable
          )
        """)
    int claimPending(@Param("claimOwner") String claimOwner, @Param("limit") int limit);

    @Update("""
        UPDATE submission_result_outbox SET
          state = 'DELIVERED',
          delivered_at = NOW(3),
          claimed_at = NULL,
          claim_owner = NULL,
          last_error = NULL
        WHERE id = #{id} AND state = 'CLAIMED' AND claim_owner = #{claimOwner}
        """)
    int markDelivered(@Param("id") String id, @Param("claimOwner") String claimOwner);

    @Update("""
        UPDATE submission_result_outbox SET
          state = CASE WHEN attempts + 1 >= #{maxAttempts} THEN 'DEAD' ELSE 'PENDING' END,
          attempts = attempts + 1,
          last_error = #{error},
          claimed_at = NULL,
          claim_owner = NULL,
          next_retry_at = DATE_ADD(NOW(3), INTERVAL POWER(2, LEAST(attempts + 1, 10)) SECOND)
        WHERE id = #{id} AND state = 'CLAIMED' AND claim_owner = #{claimOwner}
        """)
    int markFailed(@Param("id") String id, @Param("claimOwner") String claimOwner,
                   @Param("error") String error, @Param("maxAttempts") int maxAttempts);

    @Update("""
        UPDATE submission_result_outbox SET
          state = 'PENDING',
          claimed_at = NULL,
          claim_owner = NULL,
          next_retry_at = NOW(3),
          last_error = 'Reclaimed stale CLAIMED result outbox row'
        WHERE state = 'CLAIMED'
          AND (claimed_at IS NULL
               OR claimed_at < DATE_SUB(NOW(3), INTERVAL 10 MINUTE))
        """)
    int reclaimStaleClaimed();
}
