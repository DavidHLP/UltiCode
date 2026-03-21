package com.ulticode.modules.moderation.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * DTO for assigning a moderation item to a moderator.
 */
@Data
public class AssignDTO {

    /**
     * ID of the moderator to assign to
     */
    @NotBlank(message = "Assigned moderator ID is required")
    private String assignedTo;
}
