package com.ulticode.modules.email.dto;

import com.ulticode.modules.email.constants.EmailStatus;
import lombok.Data;

/**
 * DTO for querying email logs.
 */
@Data
public class EmailLogQueryDTO {

    /**
     * Filter by status
     */
    private EmailStatus status;

    /**
     * Filter by recipient email (partial match)
     */
    private String recipient;

    /**
     * Page number (1-based)
     */
    private Integer page = 1;

    /**
     * Page size
     */
    private Integer limit = 20;
}
