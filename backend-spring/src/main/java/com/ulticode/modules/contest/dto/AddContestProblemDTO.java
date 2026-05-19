package com.ulticode.modules.contest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "Add problem to contest request")
public class AddContestProblemDTO {

    @NotNull(message = "Problem ID is required")
    @Schema(description = "Problem ID to add", example = "1")
    private Long problemId;

    @Schema(description = "Score for this problem in the contest", example = "100")
    private Integer score;
}
