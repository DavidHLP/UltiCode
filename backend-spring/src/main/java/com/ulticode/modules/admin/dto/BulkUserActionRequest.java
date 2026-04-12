package com.ulticode.modules.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "Request for bulk user actions")
public class BulkUserActionRequest {

    @NotEmpty(message = "User IDs cannot be empty")
    @Size(max = 100, message = "Cannot operate on more than 100 users at once")
    @Schema(description = "List of user IDs", required = true)
    private List<String> ids;

    @Schema(description = "Reason for the action")
    private String reason;
}
