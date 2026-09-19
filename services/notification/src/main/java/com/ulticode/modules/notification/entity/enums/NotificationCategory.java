package com.ulticode.modules.notification.entity.enums;

/**
 * Notification category enum - matches Prisma schema exactly.
 *
 * <p>The {@code CONTEST} category was removed because every caller used
 * {@code NotificationCategory.SYSTEM} (or {@code COMMUNICATION}) and the
 * dispatcher's dead-branch mapping made the enum value indistinguishable from
 * a typo. A future contest-preference feature can re-introduce the value
 * alongside a dedicated preference column.
 */
public enum NotificationCategory {
    COMMUNICATION,
    MARKETING,
    SECURITY,
    SYSTEM
}
