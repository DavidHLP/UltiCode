package com.ulticode.modules.notification.entity.enums;

/**
 * Notification type enum - matches Prisma schema exactly.
 */
public enum NotificationType {
    COMMENT,
    REPLY,
    MENTION,
    UPVOTE,
    FOLLOW,
    SYSTEM,
    SUBMISSION,
    CONTEST,
CONTEST_REMINDER
}
