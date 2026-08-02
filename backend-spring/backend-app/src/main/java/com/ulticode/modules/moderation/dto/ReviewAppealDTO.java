package com.ulticode.modules.moderation.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * DTO for reviewing an appeal.
 */
@Data
public class ReviewAppealDTO {

    /**
     * Decision: APPROVED or REJECTED
     */
    @NotBlank(message = "Decision is required")
    private String decision;

    /**
     * Response from the moderator
     */
    private String response;
}
