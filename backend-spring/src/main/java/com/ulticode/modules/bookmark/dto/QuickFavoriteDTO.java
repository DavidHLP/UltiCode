package com.ulticode.modules.bookmark.dto;

import com.ulticode.modules.bookmark.entity.enums.BookmarkType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * DTO for quick favorite/unfavorite operation.
 */
@Data
public class QuickFavoriteDTO {
    @NotBlank(message = "Target ID is required")
    private String targetId;

    @NotNull(message = "Target type is required")
    private BookmarkType targetType;
}
