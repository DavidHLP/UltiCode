package com.ulticode.modules.reconciliation;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * Admin-local orphan check for the {@code audit_logs.performer_id}
 * reference to the Auth-owned {@code users} table.
 *
 * <p>Admin owns {@code audit_logs}, so this query is local; the users
 * join is a permitted Q-read (same precedent as
 * {@code GlobalRankingMapper}). Orphan predicate matches the legacy
 * scanner: child ref non-null AND parent id absent (soft-deleted
 * parents are NOT orphans).
 */
@Mapper
public interface AuditOrphanMapper {

    @Select("SELECT COUNT(*) FROM audit_logs c LEFT JOIN users p ON c.performer_id = p.id "
            + "WHERE c.performer_id IS NOT NULL AND p.id IS NULL")
    long countOrphanAuditLogs();
}
