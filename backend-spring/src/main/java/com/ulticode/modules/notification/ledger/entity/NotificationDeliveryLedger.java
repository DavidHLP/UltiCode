package com.ulticode.modules.notification.ledger.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ulticode.modules.notification.ledger.DeliveryState;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Ledger row for a single (intent_id, channel_id) delivery attempt.
 *
 * <p>Reference: wiki/concepts/notification-dispatch-and-preferences.md §2.7 (F9 修订).
 *
 * <p>The {@code id} is auto-increment for fast paging; the natural key is
 * {@code (intent_id, channel_id)} and is enforced by a UNIQUE index. {@code @Data}
 * is appropriate here because all fields are simple value types and the row
 * is read-only after the dispatcher transitions it out of CLAIMED.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("notification_delivery_ledger")
public class NotificationDeliveryLedger {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String intentId;
    private String channelId;
    private String userId;
    private String intentType;
    private DeliveryState deliveryState;
    private String failureReason;
    private LocalDateTime deliveredAt;
    private LocalDateTime updatedAt;
}
