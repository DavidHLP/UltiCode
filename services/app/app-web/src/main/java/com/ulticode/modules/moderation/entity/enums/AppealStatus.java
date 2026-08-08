package com.ulticode.modules.moderation.entity.enums;

/**
 * Appeal status enum - matches Prisma schema exactly.
 */
public enum AppealStatus {
    PENDING,
    UNDER_REVIEW,
    APPROVED,
    REJECTED
}
