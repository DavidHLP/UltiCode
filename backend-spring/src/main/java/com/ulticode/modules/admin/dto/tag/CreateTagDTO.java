package com.ulticode.modules.admin.dto.tag;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateTagDTO {

    @NotBlank(message = "Tag name is required")
    private String name;

    private String slug;

    private String description;

    private String color;

    @NotNull(message = "Tag type is required")
    private String type;
}
