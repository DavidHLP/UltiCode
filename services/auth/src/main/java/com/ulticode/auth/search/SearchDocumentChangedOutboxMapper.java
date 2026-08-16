package com.ulticode.auth.search;

import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * Mapper for {@link SearchDocumentChangedOutboxRecord}.
 *
 * <p>Insert-only for the write path plus the dispatcher's claim/deliver
 * state machine (mirrors the submission result-outbox pattern):
 * {@code PENDING → CLAIMED → DELIVERED} with stale-claim reclamation and a
 * bounded retry counter. Claim and deliver run as single-statement CAS so a
 * multi-replica dispatcher cannot double-claim.
 */
@Mapper
public interface SearchDocumentChangedOutboxMapper {

    @Insert("INSERT INTO search_document_changed_outbox "
            + "(id, owner, aggregate_id, aggregate_version, event_type, schema_version, payload, "
            + " state, attempts, created_at, next_retry_at) "
            + "VALUES (#{id}, #{owner}, #{aggregateId}, #{aggregateVersion}, #{eventType}, #{schemaVersion}, "
            + " #{payload, typeHandler=com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler}, "
            + " 'PENDING', 0, #{createdAt}, #{createdAt})")
    int insert(SearchDocumentChangedOutboxRecord record);

    @Select("<script>"
            + "SELECT id, owner, aggregate_id, aggregate_version, event_type, schema_version, payload, "
            + "       state, attempts, last_error, created_at, claimed_at, claim_owner, delivered_at, next_retry_at "
            + "FROM search_document_changed_outbox "
            + "WHERE state = 'PENDING' AND next_retry_at &lt;= NOW(3) "
            + "ORDER BY created_at ASC "
            + "LIMIT #{limit}"
            + "</script>")
    @Results({
            @Result(property = "payload", column = "payload",
                    typeHandler = com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler.class)
    })
    List<SearchDocumentChangedOutboxRecord> selectPending(@Param("limit") int limit);

    @Update("UPDATE search_document_changed_outbox "
            + "SET state = 'CLAIMED', claimed_at = NOW(3), claim_owner = #{claimOwner} "
            + "WHERE id = #{id} AND state = 'PENDING'")
    int claim(@Param("id") String id, @Param("claimOwner") String claimOwner);

    @Update("UPDATE search_document_changed_outbox "
            + "SET state = 'DELIVERED', delivered_at = NOW(3), claim_owner = NULL "
            + "WHERE id = #{id} AND state = 'CLAIMED' AND claim_owner = #{claimOwner}")
    int markDelivered(@Param("id") String id, @Param("claimOwner") String claimOwner);

    @Update("UPDATE search_document_changed_outbox "
            + "SET attempts = attempts + 1, last_error = #{error}, "
            + "    state = CASE WHEN attempts + 1 >= #{maxAttempts} THEN 'FAILED' ELSE 'PENDING' END, "
            + "    next_retry_at = DATE_ADD(NOW(3), INTERVAL #{backoffSeconds} SECOND), "
            + "    claim_owner = NULL "
            + "WHERE id = #{id} AND state = 'CLAIMED' AND claim_owner = #{claimOwner}")
    int markRetry(@Param("id") String id, @Param("claimOwner") String claimOwner,
                  @Param("error") String error, @Param("maxAttempts") int maxAttempts,
                  @Param("backoffSeconds") int backoffSeconds);

    @Update("UPDATE search_document_changed_outbox "
            + "SET state = 'PENDING', claim_owner = NULL, next_retry_at = NOW(3) "
            + "WHERE state = 'CLAIMED' AND claimed_at &lt; DATE_SUB(NOW(3), INTERVAL #{leaseSeconds} SECOND)")
    int reclaimStaleClaimed(@Param("leaseSeconds") int leaseSeconds);
}
