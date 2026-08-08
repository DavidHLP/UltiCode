package com.ulticode.modules.problemlist.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * DTO for updating a problem list.
 */
@Data
public class UpdateProblemListDTO {
    @Size(max = 100, message = "Name must not exceed 100 characters")
    private String name;

    @Size(max = 500, message = "Description must not exceed 500 characters")
    private String description;

    private Boolean isPublic;

    private String bannerTag;
    private String bannerIcon;
    private String bannerTheme;
    private Integer bannerOrder;
    private Boolean isFeatured;
}
