package com.ulticode.modules.problem.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Language configuration with starter code")
public class LanguageConfigDTO {

    @Schema(description = "Language identifier (e.g., javascript, python, java)", example = "javascript")
    private String language;

    @Schema(description = "Custom starter code template for this language", example = "function twoSum(nums, target) {\n  // Your code here\n}")
    private String starterCode;
}
