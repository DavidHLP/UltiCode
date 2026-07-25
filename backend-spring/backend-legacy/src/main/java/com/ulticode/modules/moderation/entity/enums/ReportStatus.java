package com.ulticode.modules.moderation.entity.enums;

/**
 * Report status enum - matches Prisma schema exactly.
 */
public enum ReportStatus {
    PENDING,
    REVIEWED,
    RESOLVED,
    DISMISSED
}
