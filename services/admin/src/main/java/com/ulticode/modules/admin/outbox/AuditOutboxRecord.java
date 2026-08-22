package com.ulticode.modules.admin.outbox;

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
 * Entity for the intra-JVM transaction-bound audit outbox table (P3-AUDIT-001).
 */
@Data
@TableName(value = "audit_outbox", autoResultMap = true)
public class AuditOutboxRecord {

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

    private String state; // PENDING, PROCESSING, PROCESSED, FAILED

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    private LocalDateTime claimedAt;

    private String claimOwner;

    private LocalDateTime processedAt;
}
