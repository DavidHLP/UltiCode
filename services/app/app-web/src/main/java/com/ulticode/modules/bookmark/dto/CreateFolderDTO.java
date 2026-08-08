package com.ulticode.modules.bookmark.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * DTO for creating a new bookmark folder.
 */
@Data
public class CreateFolderDTO {
    @NotBlank(message = "Folder name is required")
    @Size(max = 120, message = "Folder name must be at most 120 characters")
    private String name;

    @Size(max = 65535, message = "Description is too long")
    private String description;

    @Size(max = 50, message = "Icon must be at most 50 characters")
    private String icon;

    @Size(max = 20, message = "Color must be at most 20 characters")
    private String color;
}
