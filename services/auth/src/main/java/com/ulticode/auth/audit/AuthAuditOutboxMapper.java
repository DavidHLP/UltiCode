package com.ulticode.auth.audit;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * Local Auth audit outbox mapper (P1-AUDIT-001).
 *
 * <p>The Auth dispatcher owns claim/publish/confirm state transitions locally;
 * Admin never reads this table.
 */
@Mapper
public interface AuthAuditOutboxMapper extends BaseMapper<AuthAuditOutboxRecord> {

    @Select("""
        SELECT * FROM audit_outbox
        WHERE state = 'PENDING' AND next_retry_at <= NOW(3)
        ORDER BY created_at, id
        LIMIT #{limit}
        """)
    List<AuthAuditOutboxRecord> selectPending(@Param("limit") int limit);

    @Update("""
        UPDATE audit_outbox
        SET state = 'CLAIMED', claimed_at = NOW(3), claim_owner = #{claimOwner}
        WHERE id = #{id} AND state = 'PENDING'
        """)
    int claim(@Param("id") String id, @Param("claimOwner") String claimOwner);

    @Update("""
        UPDATE audit_outbox
        SET state = 'PENDING', claimed_at = NULL, claim_owner = NULL,
            next_retry_at = NOW(3), last_error = 'Reclaimed stale audit outbox row'
        WHERE state = 'CLAIMED'
          AND (claimed_at IS NULL OR claimed_at < DATE_SUB(NOW(3), INTERVAL 60 SECOND))
        """)
    int reclaimStaleClaimed();

    @Update("""
        UPDATE audit_outbox
        SET state = 'DELIVERED', delivered_at = NOW(3),
            claimed_at = NULL, claim_owner = NULL, last_error = NULL
        WHERE id = #{id} AND state = 'CLAIMED' AND claim_owner = #{claimOwner}
        """)
    int markDelivered(@Param("id") String id, @Param("claimOwner") String claimOwner);

    @Update("""
        UPDATE audit_outbox
        SET state = CASE WHEN attempts + 1 >= #{maxAttempts} THEN 'DEAD' ELSE 'PENDING' END,
            attempts = attempts + 1, last_error = #{error},
            claimed_at = NULL, claim_owner = NULL,
            next_retry_at = DATE_ADD(NOW(3), INTERVAL #{backoffSeconds} SECOND)
        WHERE id = #{id} AND state = 'CLAIMED' AND claim_owner = #{claimOwner}
        """)
    int markRetry(@Param("id") String id,
                  @Param("claimOwner") String claimOwner,
                  @Param("error") String error,
                  @Param("maxAttempts") int maxAttempts,
                  @Param("backoffSeconds") int backoffSeconds);
}
