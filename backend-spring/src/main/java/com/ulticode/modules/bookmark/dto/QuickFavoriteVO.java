package com.ulticode.modules.bookmark.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * View object for quick favorite response.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class QuickFavoriteVO {
    private Boolean isFavorited;
    private List<String> folderIds;
}
