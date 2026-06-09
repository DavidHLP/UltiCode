package com.ulticode.modules.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Request body for a single-submission rejudge operation.
 */
@Data
@Schema(description = "Rejudge request")
public class RejudgeRequest {

    /**
     * Whether to notify the user about the rejudge. Required so the admin
     * UI cannot accidentally omit the field and default to a silent rejudge.
     * <p>Note: no default value is set on the field, so Jackson leaves
     * {@code notifyUser} as {@code null} when the JSON body omits the key.
     * This lets {@link NotNull} reject empty bodies with HTTP 400.</p>
     */
    @NotNull(message = "notifyUser is required")
    @Schema(description = "Whether to notify the user about the rejudge", defaultValue = "false")
    private Boolean notifyUser;
}
