package com.ulticode.modules.contest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Contest problem response")
public class ContestProblemVO {

    @Schema(description = "Contest-problem mapping ID")
    private String id;

    @Schema(description = "Contest ID")
    private String contestId;

    @Schema(description = "Problem ID")
    private Long problemId;

    @Schema(description = "Problem index in the contest (e.g., A, B, C)")
    private String problemIndex;

    @Schema(description = "Score for this problem")
    private Integer score;

    @Schema(description = "Penalty per wrong submission")
    private Integer penaltyPerWrong;

    @Schema(description = "Problem title")
    private String title;

    @Schema(description = "Problem slug")
    private String slug;

    @Schema(description = "Problem difficulty")
    private String difficulty;

    @Schema(description = "Solved count for this problem in the contest")
    private Integer solvedCount;

    @Schema(description = "Submission count for this problem in the contest")
    private Integer submissionCount;

    @Schema(description = "Acceptance rate")
    private java.math.BigDecimal acceptanceRate;
}
