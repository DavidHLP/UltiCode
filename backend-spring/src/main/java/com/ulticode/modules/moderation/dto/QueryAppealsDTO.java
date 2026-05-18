package com.ulticode.modules.moderation.dto;

import lombok.Data;

/**
 * DTO for querying appeals.
 */
@Data
public class QueryAppealsDTO {

    private String status;

    private String queueId;

    private String appellantId;

    private String sortBy;

    private String sortOrder;

    private Integer page = 1;

    private Integer limit = 20;
}
