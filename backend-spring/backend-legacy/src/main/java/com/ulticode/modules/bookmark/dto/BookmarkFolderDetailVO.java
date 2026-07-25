package com.ulticode.modules.bookmark.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * View object for bookmark folder with bookmarks detail.
 */
@Data
public class BookmarkFolderDetailVO {
    private String id;
    private String name;
    private String description;
    private String icon;
    private String color;
    private Integer sortOrder;
    private Boolean isDefault;
    private List<BookmarkVO> items;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
