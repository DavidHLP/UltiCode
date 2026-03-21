package com.ulticode.modules.moderation.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * View object for appeals.
 */
@Data
public class AppealVO {

    private String id;
    private String queueId;
    private String appellantId;
    private String appellantName;
    private String appellantUsername;
    private String reason;
    private String evidence;
    private String status;
    private String reviewedById;
    private String reviewedByName;
    private LocalDateTime reviewedAt;
    private String response;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
