package com.ulticode.modules.event.outbox;

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
 * Entity for the {@code integration_outbox} table (P6-OUTBOX-001).
 *
 * <p>Durable cross-service event envelope written in the same DB transaction as
 * the business operation. A dispatcher claims PENDING rows, publishes to Redis
 * Streams, and marks them DELIVERED only after XADD succeeds.
 *
 * <p>State machine: PENDING → CLAIMED → DELIVERED (or → DEAD after max attempts).
 *
 * <p>Table lives in the default schema (shared by the monolith). In the per-owner
 * split deployment, it will move to the {@code admin} schema alongside {@code audit_outbox}
 * as the cross-service governance surface.
 */
@Data
@TableName(value = "integration_outbox", autoResultMap = true)
public class IntegrationOutboxRecord {

    @TableId(type = IdType.ASSIGN_UUID)
    @TableField("event_id")
    private String eventId;

    /** Publishing Owner: Auth, Admin, or App. */
    private String owner;

    @TableField("aggregate_id")
    private String aggregateId;

    @TableField("aggregate_version")
    private Long aggregateVersion;

    @TableField("causation_id")
    private String causationId;

    @TableField("trace_id")
    private String traceId;

    @TableField("event_type")
    private String eventType;

    @TableField("schema_version")
    private Integer schemaVersion;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> payload;

    /** PENDING, CLAIMED, DELIVERED, DEAD */
    private String state;

    private Integer attempts;

    @TableField("last_error")
    private String lastError;

    /** Redis Streams XADD return ID, set after successful publish. */
    @TableField("stream_id")
    private String streamId;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField("claimed_at")
    private LocalDateTime claimedAt;

    /** Dispatcher instance that currently owns the CLAIMED lease. */
    @TableField("claim_owner")
    private String claimOwner;

    @TableField("delivered_at")
    private LocalDateTime deliveredAt;

    @TableField("next_retry_at")
    private LocalDateTime nextRetryAt;
}
