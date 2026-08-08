package com.ulticode.modules.moderation.dto;

import lombok.Data;

/**
 * DTO for querying reports.
 */
@Data
public class QueryReportsDTO {

    private String status;

    private String category;

    private String reporterId;

    private String entityType;

    private String entityId;

    private String sortBy;

    private String sortOrder;

    private Integer page = 1;

    private Integer limit = 20;
}
