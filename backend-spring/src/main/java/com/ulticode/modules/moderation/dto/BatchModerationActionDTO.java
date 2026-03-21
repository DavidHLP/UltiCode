package com.ulticode.modules.moderation.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * DTO for batch moderation actions.
 */
@Data
public class BatchModerationActionDTO {

    /**
     * List of queue item IDs to perform the action on
     */
    @NotEmpty(message = "Queue item IDs are required")
    private List<String> queueIds;

    /**
     * Action to perform on all items
     */
    @NotNull(message = "Action is required")
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
