package com.ulticode.modules.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * Request body for rejudge operation.
 */
@Data
@Schema(description = "Rejudge request")
public class RejudgeRequest {

    @Schema(description = "Whether to notify the user about rejudge")
    private Boolean notifyUser = false;
}
