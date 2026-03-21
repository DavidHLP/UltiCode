package com.ulticode.modules.bookmark.dto;

import com.ulticode.modules.bookmark.entity.enums.BookmarkType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * DTO for adding a bookmark to a folder.
 */
@Data
public class AddBookmarkDTO {
    @NotBlank(message = "Target ID is required")
    private String targetId;

    @NotNull(message = "Target type is required")
    private BookmarkType targetType;

    private String note;
}
