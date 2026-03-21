package com.ulticode.modules.moderation.entity.enums;

/**
 * Moderation status enum - matches Prisma schema exactly.
 */
public enum ModerationStatus {
    PENDING,
    UNDER_REVIEW,
    RESOLVED,
    DISMISSED,
    APPEAL_PENDING
}
