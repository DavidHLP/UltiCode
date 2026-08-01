package com.ulticode.modules.subscription.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Subscription entity representing user subscriptions.
 */
@Data
@TableName("subscriptions")
public class Subscription {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /**
     * User ID who owns this subscription
     */
    private String userId;

    /**
     * Subscription plan: FREE, PREMIUM_MONTHLY, PREMIUM_YEARLY
     */
    private String plan;

    /**
     * Subscription status: ACTIVE, EXPIRED, CANCELLED, PENDING
     */
    private String status;

    /**
     * When the subscription started
     */
    private LocalDateTime startedAt;

    /**
     * When the subscription expires
     */
    private LocalDateTime expiresAt;

    /**
     * When the subscription was cancelled (if cancelled)
     */
    private LocalDateTime cancelledAt;

    /**
     * Payment transaction ID (if applicable)
     */
    private String transactionId;

    /**
     * Auto-renewal flag
     */
    private Boolean autoRenew;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Boolean isDeleted;

    private LocalDateTime deletedAt;
}
