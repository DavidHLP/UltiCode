package com.ulticode.modules.reconciliation;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** Admin-local audit performer candidates; Auth parent existence is resolved via RPC. */
@Mapper
public interface AuditOrphanMapper {

    @Select("""
            SELECT performer_id, COUNT(*) AS row_count
            FROM audit_logs
            WHERE performer_id IS NOT NULL
            GROUP BY performer_id
            ORDER BY performer_id
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<AuditReferenceCount> auditPerformerIds(
            @Param("offset") int offset, @Param("limit") int limit);
}
