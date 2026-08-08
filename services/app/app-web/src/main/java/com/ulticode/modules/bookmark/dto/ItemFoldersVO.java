package com.ulticode.modules.bookmark.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * View object for folders containing a specific item.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ItemFoldersVO {
    private String targetId;
    private String targetType;
    private Boolean isFavorited;
    private List<BookmarkFolderVO> folders;
}
