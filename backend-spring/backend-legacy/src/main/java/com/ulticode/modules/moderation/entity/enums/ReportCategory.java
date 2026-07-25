package com.ulticode.modules.moderation.entity.enums;

/**
 * Report category enum - matches Prisma schema exactly.
 */
public enum ReportCategory {
    SPAM,
    HARASSMENT,
    HATE_SPEECH,
    VIOLENCE,
    SEXUAL_CONTENT,
    MISINFORMATION,
    WRONG_ANSWER,
    COPYRIGHT,
    OTHER
}
