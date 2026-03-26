package com.ulticode.modules.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * Result of a batch rejudge operation.
 */
@Data
@Schema(description = "Batch rejudge operation result")
public class BatchRejudgeResponse {

    @Schema(description = "Individual rejudge results")
    private List<RejudgeResult> results;

    @Schema(description = "Total number of submissions in batch")
    private Integer total;

    @Schema(description = "Number of successfully initiated rejudges")
    private Integer successful;

    @Schema(description = "Number of failed rejudges")
    private Integer failed;
}
