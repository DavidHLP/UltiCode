package com.ulticode.modules.moderation.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * DTO for creating a new appeal.
 */
@Data
public class CreateAppealDTO {

    /**
     * ID of the moderation queue item being appealed
     */
    @NotBlank(message = "Queue ID is required")
    private String queueId;

    /**
     * Reason for the appeal
     */
    @NotBlank(message = "Reason is required")
    private String reason;

    /**
     * Evidence supporting the appeal
     */
    private String evidence;
}
