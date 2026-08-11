package com.ulticode.modules.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Admin request DTO for creating a problem list. Mirrors the legacy
 * problem-list create payload so the HTTP contract is unchanged.
 */
@Data
public class CreateProblemListRequest {

    @NotBlank(message = "Name required")
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
