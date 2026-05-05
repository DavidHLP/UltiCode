package com.ulticode.modules.admin.dto.tag;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateTagDTO {

    private String name;

    private String slug;

    private String description;

    private String color;

    @NotNull(message = "Tag type is required")
    private String type;
}
