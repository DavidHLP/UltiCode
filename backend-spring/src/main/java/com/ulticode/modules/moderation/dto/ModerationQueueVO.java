package com.ulticode.modules.moderation.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * View object for moderation queue items.
 */
@Data
public class ModerationQueueVO {

    private String id;
    private String entityType;
    private String entityId;
    private String parentId;
    private String authorId;
    private String authorName;
    private String authorUsername;
    private Integer priority;
    private String status;
    private Integer reportCount;
    private String primaryCategory;
    private String assignedToId;
    private String assignedToName;
    private String assignedToUsername;
    private LocalDateTime assignedAt;
    private String reviewedById;
    private String reviewedByName;
    private LocalDateTime reviewedAt;
    private String resolution;
    private String resolutionNote;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime resolvedAt;
}
