package com.ulticode.modules.moderation.dto;

import lombok.Data;

/**
 * DTO for querying moderation queue items.
 */
@Data
public class QueryModerationQueueDTO {

    private String status;

    private String entityType;

    private String assignedTo;

    private String primaryCategory;

    private Integer minPriority;

    private String sortBy;

    private String sortOrder;

    private Integer page = 1;

    private Integer limit = 20;
}
