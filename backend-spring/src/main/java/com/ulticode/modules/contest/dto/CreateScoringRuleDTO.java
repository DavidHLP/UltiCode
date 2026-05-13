package com.ulticode.modules.contest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Data;

/**
 * DTO for creating a new scoring rule.
 */
@Data
@Schema(description = "Create scoring rule request")
public class CreateScoringRuleDTO {

    @NotBlank(message = "Name is required")
    @Size(max = 255, message = "Name must not exceed 255 characters")
    @Schema(description = "Rule name", example = "Standard ICPC Scoring")
    private String name;

    @Size(max = 2000, message = "Description must not exceed 2000 characters")
    @Schema(description = "Rule description", example = "Standard ICPC scoring with time bonus and penalty")
    private String description;

    @NotNull(message = "Base score per problem is required")
    @Min(value = 0, message = "Base score must be at least 0")
    @Max(value = 10000, message = "Base score must not exceed 10000")
    @Schema(description = "Base score awarded for each solved problem", example = "100")
    private Integer baseScorePerProblem;

    @NotNull(message = "Time bonus per minute is required")
    @Min(value = 0, message = "Time bonus must be at least 0")
    @Max(value = 1000, message = "Time bonus must not exceed 1000")
    @Schema(description = "Bonus points per minute remaining", example = "2")
    private Integer timeBonusPerMinute;

    @NotNull(message = "Wrong answer penalty is required")
    @Min(value = 0, message = "Wrong answer penalty must be at least 0")
    @Max(value = 1000, message = "Wrong answer penalty must not exceed 1000")
    @Schema(description = "Penalty points per wrong answer", example = "50")
    private Integer wrongAnswerPenalty;

    @Min(value = 0, message = "Time limit penalty must be at least 0")
    @Max(value = 10000, message = "Time limit penalty must not exceed 10000")
    @Schema(description = "Penalty for exceeding time limit", example = "0")
    private Integer timeLimitPenalty;

    @NotNull(message = "First solve bonus is required")
    @Min(value = 0, message = "First solve bonus must be at least 0")
    @Max(value = 5000, message = "First solve bonus must not exceed 5000")
    @Schema(description = "Bonus points for first solve", example = "50")
    private Integer firstSolveBonus;

    @Min(value = 0, message = "Full score bonus must be at least 0")
    @Max(value = 5000, message = "Full score bonus must not exceed 5000")
    @Schema(description = "Bonus for achieving full score", example = "100")
    private Integer fullScoreBonus;

    @Schema(description = "Whether this is the default rule", example = "false")
    private Boolean isDefault;
}
