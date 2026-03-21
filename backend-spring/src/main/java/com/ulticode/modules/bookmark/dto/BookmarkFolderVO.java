package com.ulticode.modules.bookmark.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * View object for bookmark folder response.
 */
@Data
public class BookmarkFolderVO {
    private String id;
    private String name;
    private String description;
    private String icon;
    private String color;
    private Integer sortOrder;
    private Boolean isDefault;
    private Integer itemCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
