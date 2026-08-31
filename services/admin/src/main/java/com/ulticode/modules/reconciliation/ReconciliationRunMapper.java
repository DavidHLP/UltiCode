package com.ulticode.modules.reconciliation;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * MyBatis-Plus mapper for {@link ReconciliationRun} (P5-RECONCILE-001).
 */
@Mapper
public interface ReconciliationRunMapper extends BaseMapper<ReconciliationRun> {

    /**
     * Finish a run only while the same owner and fence token still hold the
     * database lease. A stale runner therefore cannot publish completion.
     */
    @Update("""
            UPDATE reconciliation_runs AS r
            JOIN fenced_job_leases AS l ON l.lease_name = #{leaseName}
            SET r.finished_at = #{run.finishedAt},
                r.status = #{run.status},
                r.divergence_count = #{run.divergenceCount},
                r.orphan_count = #{run.orphanCount},
                r.detail = #{run.detail}
            WHERE r.run_id = #{run.runId}
              AND r.fence_token = #{run.fenceToken}
              AND l.owner_token = #{ownerToken}
              AND l.fence_token = #{fenceToken}
              AND l.leased_until > CURRENT_TIMESTAMP(3)
            """)
    int updateByIdWhileLeaseHeld(@Param("run") ReconciliationRun run,
                                 @Param("leaseName") String leaseName,
                                 @Param("ownerToken") String ownerToken,
                                 @Param("fenceToken") long fenceToken);
}
