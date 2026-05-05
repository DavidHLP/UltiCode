package com.ulticode.modules.admin.dto.tag;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class MergeTagDTO {

    @NotBlank(message = "Source tag ID is required")
    private String sourceId;

    @NotBlank(message = "Target tag ID is required")
    private String targetTagId;

    @NotNull(message = "Tag type is required")
    private String type;
}
