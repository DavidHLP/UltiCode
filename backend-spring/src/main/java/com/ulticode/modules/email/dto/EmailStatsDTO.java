package com.ulticode.modules.email.dto;

import lombok.Data;

/**
 * DTO for email statistics.
 */
@Data
public class EmailStatsDTO {

    /**
     * Total number of emails
     */
    private Long total;

    /**
     * Number of sent emails
     */
    private Long sent;

    /**
     * Number of pending emails
     */
    private Long pending;

    /**
     * Number of failed emails
     */
    private Long failed;
}
