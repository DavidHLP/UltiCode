package com.ulticode.modules.bookmark.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * DTO for reordering bookmark folders.
 */
@Data
public class ReorderFoldersDTO {
    @NotEmpty(message = "Folder IDs list cannot be empty")
    private List<String> folderIds;
}
