package com.ulticode.modules.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "Request to ban a user")
public class BanUserRequest {

    @Size(max = 1000, message = "Reason must be at most 1000 characters")
    @Schema(description = "Reason for the ban")
    private String reason;

    @Schema(description = "Ban duration end date (ISO-8601). Null means permanent.")
    private String until;
}
