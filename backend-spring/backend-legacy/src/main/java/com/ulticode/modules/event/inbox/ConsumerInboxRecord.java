package com.ulticode.modules.event.inbox;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Entity for the {@code consumer_inbox} table (P6-INBOX-001).
 *
 * <p>Provides at-least-once delivery with exactly-once processing: the
 * {@code (consumer, event_id)} unique constraint prevents duplicate processing.
 *
 * <p>State machine: PENDING → PROCESSING → PROCESSED (or → DEAD after max attempts).
 * Stale PROCESSING rows (lease expired) are reclaimed by the next lease holder.
 */
@Data
@TableName(value = "consumer_inbox", autoResultMap = true)
public class ConsumerInboxRecord {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /** Consumer name (e.g., App, Admin, Auth). */
    private String consumer;

    @TableField("event_id")
    private String eventId;

    @TableField("event_type")
    private String eventType;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> payload;

    /** PENDING, PROCESSING, PROCESSED, DEAD */
    private String state;

    private Integer attempts;

    @TableField("last_error")
    private String lastError;

    @TableField("lease_owner")
    private String leaseOwner;

    @TableField("lease_expires_at")
    private LocalDateTime leaseExpiresAt;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("processed_at")
    private LocalDateTime processedAt;

    @TableField("next_retry_at")
    private LocalDateTime nextRetryAt;
}
