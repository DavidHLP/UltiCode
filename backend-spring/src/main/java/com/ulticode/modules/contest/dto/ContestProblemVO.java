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
}
