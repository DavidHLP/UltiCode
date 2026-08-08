package com.ulticode.modules.admin.dto.problem;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "Request to batch moderate flagged problems")
public class BatchModerateRequestDTO {

    @NotEmpty(message = "IDs list cannot be empty")
    @Schema(description = "List of problem IDs to moderate")
    private List<String> ids;

    @NotNull(message = "Status is required")
    @Pattern(regexp = "REVIEWED|RESOLVED|DISMISSED", message = "Status must be REVIEWED, RESOLVED, or DISMISSED")
    @Schema(description = "Moderation status", allowableValues = {"REVIEWED", "RESOLVED", "DISMISSED"})
    private String status;

    @Schema(description = "Optional moderation notes")
    private String notes;
}
