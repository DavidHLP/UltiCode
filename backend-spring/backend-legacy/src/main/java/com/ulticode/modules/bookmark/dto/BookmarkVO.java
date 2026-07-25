package com.ulticode.modules.bookmark.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * View object for bookmark item response.
 */
@Data
public class BookmarkVO {
    private String id;
    private String folderId;
    private String targetId;
    private String targetType;
    private Integer sortOrder;
    private String note;
    private LocalDateTime createdAt;
}
