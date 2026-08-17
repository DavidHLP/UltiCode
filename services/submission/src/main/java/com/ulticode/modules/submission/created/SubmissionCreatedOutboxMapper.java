package com.ulticode.modules.submission.created;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** MyBatis mapper for the durable SubmissionCreated event outbox. */
@Mapper
public interface SubmissionCreatedOutboxMapper
        extends BaseMapper<SubmissionCreatedOutboxRecord> {

    @Select("""
        SELECT * FROM submission_created_outbox
        WHERE submission_id = #{submissionId}
        ORDER BY generation DESC
        LIMIT 1
        """)
    SubmissionCreatedOutboxRecord findLatestBySubmissionId(
            @Param("submissionId") String submissionId);

    @Insert("""
        INSERT INTO submission_created_outbox
          (id, submission_id, generation, user_id, problem_id, contest_id,
           virtual_session_id, language, occurred_at, state, attempts, next_retry_at)
        VALUES
          (#{id}, #{submissionId}, #{generation}, #{userId}, #{problemId}, #{contestId},
           #{virtualSessionId}, #{language}, #{occurredAt}, 'PENDING', 0, NOW(3))
        ON DUPLICATE KEY UPDATE id = id
        """)
    int insertIfAbsent(@Param("id") String id,
                       @Param("submissionId") String submissionId,
                       @Param("generation") long generation,
                       @Param("userId") String userId,
                       @Param("problemId") String problemId,
                       @Param("contestId") String contestId,
                       @Param("virtualSessionId") String virtualSessionId,
                       @Param("language") String language,
                       @Param("occurredAt") java.time.LocalDateTime occurredAt);

    @Update("""
        UPDATE submission_created_outbox SET
          state = 'CLAIMED', claimed_at = NOW(3), claim_owner = #{claimOwner}
        WHERE state = 'PENDING' AND next_retry_at <= NOW(3)
          AND id IN (
            SELECT id FROM (
              SELECT id FROM submission_created_outbox
              WHERE state = 'PENDING' AND next_retry_at <= NOW(3)
              ORDER BY created_at LIMIT #{limit}
            ) AS claimable
          )
        """)
    int claimPending(@Param("claimOwner") String claimOwner, @Param("limit") int limit);

    @Update("""
        UPDATE submission_created_outbox SET
          state = 'DELIVERED', delivered_at = NOW(3), claimed_at = NULL,
          claim_owner = NULL, last_error = NULL
        WHERE id = #{id} AND state = 'CLAIMED' AND claim_owner = #{claimOwner}
        """)
    int markDelivered(@Param("id") String id, @Param("claimOwner") String claimOwner);

    @Update("""
        UPDATE submission_created_outbox SET
          state = CASE WHEN attempts + 1 >= #{maxAttempts} THEN 'DEAD' ELSE 'PENDING' END,
          attempts = attempts + 1, last_error = #{error}, claimed_at = NULL,
          claim_owner = NULL,
          next_retry_at = DATE_ADD(NOW(3), INTERVAL POWER(2, LEAST(attempts + 1, 10)) SECOND)
        WHERE id = #{id} AND state = 'CLAIMED' AND claim_owner = #{claimOwner}
        """)
    int markFailed(@Param("id") String id, @Param("claimOwner") String claimOwner,
                   @Param("error") String error, @Param("maxAttempts") int maxAttempts);

    @Update("""
        UPDATE submission_created_outbox SET
          state = 'PENDING', claimed_at = NULL, claim_owner = NULL,
          next_retry_at = NOW(3), last_error = 'Reclaimed stale CLAIMED created outbox row'
        WHERE state = 'CLAIMED'
          AND (claimed_at IS NULL OR claimed_at < DATE_SUB(NOW(3), INTERVAL 10 MINUTE))
        """)
    int reclaimStaleClaimed();
}
