package com.ulticode.modules.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "Request to flag content for review")
public class FlagRequest {

    @Size(max = 1000, message = "Reason must be at most 1000 characters")
    @Schema(description = "Reason for flagging")
    private String reason;
}
