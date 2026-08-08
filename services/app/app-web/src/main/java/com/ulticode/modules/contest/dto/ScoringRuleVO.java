package com.ulticode.modules.contest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Scoring Rule View Object for API responses.
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Scoring rule response")
public class ScoringRuleVO {

    @Schema(description = "Rule unique identifier")
    private String id;

    @Schema(description = "Rule name")
    private String name;

    @Schema(description = "Rule description")
    private String description;

    @Schema(description = "Base score per solved problem")
    private Integer baseScorePerProblem;

    @Schema(description = "Time bonus per minute remaining")
    private Integer timeBonusPerMinute;

    @Schema(description = "Penalty per wrong answer")
    private Integer wrongAnswerPenalty;

    @Schema(description = "Penalty for exceeding time limit")
    private Integer timeLimitPenalty;

    @Schema(description = "Bonus for first solve")
    private Integer firstSolveBonus;

    @Schema(description = "Bonus for full score")
    private Integer fullScoreBonus;

    @Schema(description = "Whether this is the default rule")
    private Boolean isDefault;

    @Schema(description = "Whether the rule is active")
    private Boolean isActive;

    @Schema(description = "Creation timestamp")
    private LocalDateTime createdAt;

    @Schema(description = "Last update timestamp")
    private LocalDateTime updatedAt;

    @Schema(description = "Number of contests using this rule")
    private Long contestCount;
}
