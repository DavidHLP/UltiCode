package com.ulticode.modules.problemlist.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * DTO for updating a category.
 */
@Data
public class UpdateCategoryDTO {
    @Size(max = 100, message = "Name must not exceed 100 characters")
    private String name;

    @Size(max = 500, message = "Description must not exceed 500 characters")
    private String description;

    private String icon;
    private String color;
    private Integer sortOrder;
}
