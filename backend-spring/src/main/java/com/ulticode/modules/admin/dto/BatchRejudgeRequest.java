package com.ulticode.modules.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * Request body for batch rejudge operation.
 */
@Data
@Schema(description = "Batch rejudge request")
public class BatchRejudgeRequest {

    @Schema(description = "List of submission IDs to rejudge")
    private List<String> ids;

    @Schema(description = "Whether to notify users about rejudge")
    private Boolean notifyUsers = false;
}
