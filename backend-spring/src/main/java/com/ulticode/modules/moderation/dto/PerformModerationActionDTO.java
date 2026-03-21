package com.ulticode.modules.moderation.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * DTO for performing a moderation action.
 */
@Data
public class PerformModerationActionDTO {

    /**
     * Action to perform (e.g., DELETED, WARNED, TEMP_BANNED, DISMISSED)
     */
    @NotBlank(message = "Action is required")
    private String action;

    /**
     * Additional notes about the action
     */
    private String note;

    /**
     * Duration in days (for temporary bans)
     */
    private Integer durationDays;
}
