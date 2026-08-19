package com.ulticode.modules.reconciliation;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** Admin-local audit performer candidates; Auth parent existence is resolved via RPC. */
@Mapper
public interface AuditOrphanMapper {

    @Select("SELECT performer_id FROM audit_logs WHERE performer_id IS NOT NULL")
    List<String> auditPerformerIds();
}
