package com.ulticode.auth.audit;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Auth-owned projection of the cross-owner {@code admin.audit_outbox} table
 * (P7-AUDIT-SINK-OWNER-BINDING-001).
 *
 * <p>Mirrors the admin-owned {@code AuditOutboxRecord} field-for-field, but is
 * schema-qualified: the Auth datasource connects as {@code auth_rw} whose
 * default schema is {@code auth}, while {@code audit_outbox} lives in the
 * {@code admin} schema. The {@code auth_rw} user holds an INSERT-only grant on
 * {@code admin.audit_outbox} (V20260729140000), which is exactly the
 * append-only audit seam this record writes through.
 */
@Data
@TableName(value = "admin.audit_outbox", autoResultMap = true)
public class AuthAuditOutboxRecord {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String performerId;
    private String userId;
    private String action;
    private String entityType;
    private String entityId;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> oldValues;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> newValues;

    private String ipAddress;
    private String userAgent;

    private String state; // PENDING, PROCESSED, FAILED

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    private LocalDateTime processedAt;
}
