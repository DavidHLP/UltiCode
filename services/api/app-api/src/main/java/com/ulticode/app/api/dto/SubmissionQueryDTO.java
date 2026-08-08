package com.ulticode.app.api.dto;

import lombok.Data;
import java.io.Serializable;

/**
 * Query DTO for listing submissions.
 */
@Data
public class SubmissionQueryDTO implements Serializable {

    /**
     * Page number (1-based)
     */
    private Integer page = 1;

    /**
     * Number of items per page
     */
    private Integer pageSize = 10;

    /**
     * Filter by problem ID
     */
    private Long problemId;

    /**
     * Filter by user ID
     */
    private String userId;

    /**
     * Filter by status
     */
    private String status;

    /**
     * Filter by language
     */
    private String language;
}
