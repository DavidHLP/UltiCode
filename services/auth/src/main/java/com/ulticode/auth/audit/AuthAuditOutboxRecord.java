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
 * Auth-owned audit outbox row (P1-AUDIT-001).
 *
 * <p>The Auth datasource writes this unqualified table in its own schema. A
 * separate dispatcher publishes the row as an {@code AuditRecorded} event;
 * Admin consumes that event through its durable inbox.
 */
@Data
@TableName(value = "audit_outbox", autoResultMap = true)
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

    private String state; // PENDING, CLAIMED, DELIVERED, DEAD

    private Integer attempts;

    private String lastError;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    private LocalDateTime claimedAt;

    private String claimOwner;

    private LocalDateTime deliveredAt;

    private LocalDateTime nextRetryAt;
}
