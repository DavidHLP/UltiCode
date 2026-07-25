package com.ulticode.modules.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Request DTO for flagging a single solution.
 */
@Data
public class FlagSolutionDto {

    @NotBlank(message = "Flag reason is required")
    @Size(max = 500, message = "Flag reason must not exceed 500 characters")
    private String reason;
}
