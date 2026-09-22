package com.ulticode.app.storage;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * App-owned durable cleanup intent for a replaced avatar object.
 *
 * <p>The row is inserted inside the same transaction as the profile mutation
 * that displaced the object, so a crash or a transient storage failure cannot
 * orphan the previous avatar forever; the dispatcher deletes it with retry.
 */
@Data
@TableName("storage_cleanup_outbox")
public class StorageCleanupOutboxRecord {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String objectKey;

    private String state; // PENDING, CLAIMED, DELIVERED, DEAD

    private Integer attempts;

    private String lastError;

    private LocalDateTime nextRetryAt;

    private LocalDateTime claimedAt;

    private String claimOwner;

    private LocalDateTime createdAt;

    private LocalDateTime deliveredAt;
}
