package com.ulticode.modules.moderation.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * DTO for creating a new report.
 */
@Data
public class CreateReportDTO {

    /**
     * Type of the entity being reported
     */
    @NotBlank(message = "Entity type is required")
    private String entityType;

    /**
     * ID of the entity being reported
     */
    @NotBlank(message = "Entity ID is required")
    private String entityId;

    /**
     * Category of the report (e.g., SPAM, HARASSMENT)
     */
    @NotBlank(message = "Category is required")
    private String category;

    /**
     * Detailed reason for the report
     */
    private String reason;

    /**
     * Evidence supporting the report
     */
    private String evidence;
}
