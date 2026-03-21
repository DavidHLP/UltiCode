package com.ulticode.modules.moderation.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * View object for reports.
 */
@Data
public class ReportVO {

    private String id;
    private String reporterId;
    private String reporterName;
    private String reporterUsername;
    private String entityType;
    private String entityId;
    private String category;
    private String reason;
    private String evidence;
    private String status;
    private String queueId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
