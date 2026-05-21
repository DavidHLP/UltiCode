package com.ulticode.modules.problemlist.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * DTO for creating a new problem list.
 */
@Data
public class CreateProblemListDTO {
    @NotBlank(message = "Name is required")
    @Size(max = 100, message = "Name must not exceed 100 characters")
    private String name;

    @Size(max = 500, message = "Description must not exceed 500 characters")
    private String description;

    private Boolean isPublic = false;

    private String bannerTag;
    private String bannerIcon;
    private String bannerTheme;
    private Integer bannerOrder;
}
