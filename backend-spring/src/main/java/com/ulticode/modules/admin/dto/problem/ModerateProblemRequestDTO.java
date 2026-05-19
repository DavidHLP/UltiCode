package com.ulticode.modules.admin.dto.problem;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "Request to moderate a flagged problem")
public class ModerateProblemRequestDTO {

    @NotNull(message = "Status is required")
    @Schema(description = "Moderation status", allowableValues = {"PENDING", "REVIEWED", "RESOLVED", "DISMISSED"})
    private String status;

    @Schema(description = "Optional moderation notes")
    private String notes;
}
