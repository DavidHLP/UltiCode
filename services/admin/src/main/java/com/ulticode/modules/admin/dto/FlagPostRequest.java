package com.ulticode.modules.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * DTO for flagging a forum post.
 */
@Data
@Schema(description = "Flag post request")
public class FlagPostRequest {

    @NotBlank(message = "Flag reason cannot be blank")
    @Schema(description = "Reason for flagging", example = "Inappropriate content")
    private String reason;
}
