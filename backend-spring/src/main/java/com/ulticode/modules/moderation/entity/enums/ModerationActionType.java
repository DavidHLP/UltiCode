package com.ulticode.modules.moderation.entity.enums;

/**
 * Moderation action type enum - matches Prisma schema exactly.
 */
public enum ModerationActionType {
    DELETED,
    HIDDEN,
    RESTORED,
    WARNED,
    TEMP_BANNED,
    PERM_BANNED,
    DISMISSED,
    RESOLVED,
    APPEAL_PENDING,
    APPEAL_APPROVED,
    APPEAL_REJECTED
}
