package com.ulticode.modules.submission.mapper;

import com.ulticode.submission.api.dto.SubmissionUserReferenceCountDTO;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/** Bounded owner-local Submission facts for reconciliation. */
@Mapper
public interface SubmissionReconciliationReadMapper {

    @ConstructorArgs({
            @Arg(column = "account_id", javaType = String.class),
            @Arg(column = "row_count", javaType = Long.class)
    })
    @Select("""
        SELECT user_id AS account_id, COUNT(*) AS row_count
        FROM submissions
        WHERE user_id IS NOT NULL
          AND user_id > #{afterAccountId}
          AND (#{createdSince,jdbcType=TIMESTAMP} IS NULL
               OR created_at >= #{createdSince,jdbcType=TIMESTAMP})
        GROUP BY user_id
        ORDER BY user_id
        LIMIT #{limit}
        """)
    List<SubmissionUserReferenceCountDTO> findUserReferenceCounts(
            @Param("afterAccountId") String afterAccountId,
            @Param("createdSince") LocalDateTime createdSince,
            @Param("limit") int limit);
}
