package com.ulticode.modules.notification.entity.enums;

/**
 * Notification category enum - matches Prisma schema exactly.
 *
 * <p>ADR-004 M4d-1 finding #7: {@code CONTEST} was removed in M4d-1 because
 * every caller used {@code NotificationCategory.SYSTEM} (or
 * {@code COMMUNICATION}) and the dispatcher's dead-branch mapping
 * (CONTEST → {@code preference.getSystemEnabled()}) made the enum value
 * indistinguishable from a typo. A future "contest preferences" ADR can
 * re-introduce the value alongside a dedicated preference column.
 */
public enum NotificationCategory {
    COMMUNICATION,
    MARKETING,
    SECURITY,
    SYSTEM
}
