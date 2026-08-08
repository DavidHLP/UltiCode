package com.ulticode.modules.moderation.dto;

import com.ulticode.modules.moderation.entity.enums.ModerationActionType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * DTO for performing a moderation action.
 */
@Data
public class PerformModerationActionDTO {

    /**
     * Action to perform (e.g., DELETED, WARNED, TEMP_BANNED, DISMISSED)
     */
    @NotNull(message = "Action is required")
    private ModerationActionType action;

    /**
     * Additional notes about the action
     */
    private String note;

    /**
     * Duration in days (for temporary bans)
     */
    @Min(value = 1, message = "Duration must be at least 1 day")
    @Max(value = 3650, message = "Duration cannot exceed 3650 days (10 years)")
    private Integer durationDays;
}