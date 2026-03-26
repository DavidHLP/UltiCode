package com.ulticode.modules.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * Request DTO for bulk forum post actions.
 */
@Data
@Schema(description = "Bulk action request for forum posts")
public class BulkActionRequest {

    @NotEmpty(message = "Post IDs cannot be empty")
    @Schema(description = "List of post IDs to perform action on", required = true)
    private List<String> ids;

    @NotBlank(message = "Action cannot be blank")
    @Schema(description = "Action to perform: delete, pin, unpin, lock, unlock, unflag", required = true)
    private String action;
}
