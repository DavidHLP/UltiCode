package com.ulticode.modules.bookmark.dto;

import lombok.Data;

/**
 * DTO for updating a bookmark note.
 */
@Data
public class UpdateBookmarkDTO {
    private String note;
    private Integer sortOrder;
}
