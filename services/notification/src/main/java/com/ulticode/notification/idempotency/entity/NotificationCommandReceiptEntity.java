package com.ulticode.notification.idempotency.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Notification-owned durable command receipt for RPC replay-dedup.
 *
 * <p>Created atomically within the same transaction as the profile mutation
 * (unlike the Auth precedent's post-commit insert). When a retried command
 * carries the same {@code (service, operation, idempotency_key)}, the stored
 * result is replayed instead of re-executing.
 */
@Data
@TableName("notification_command_receipt")
public class NotificationCommandReceiptEntity {

    @TableId
    private String id;
    private String commandId;
    private String service;
    private String operation;
    private String idempotencyKey;
    private String requestFingerprint;
    private String status;
    private String errorCode;
    private String resultPayload;
    private String actorType;
    private String actorId;
    private String traceId;
    private LocalDateTime createdAt;
}
