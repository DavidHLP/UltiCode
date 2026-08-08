package com.ulticode.modules.admin.dto.problem;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "Request to flag a problem")
public class FlagProblemRequestDTO {

    @NotBlank(message = "Reason is required")
    @Schema(description = "Reason for flagging the problem")
    private String reason;
}
