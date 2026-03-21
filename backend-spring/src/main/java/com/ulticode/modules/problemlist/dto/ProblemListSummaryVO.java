package com.ulticode.modules.problemlist.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * View object for problem list summary response.
 */
@Data
public class ProblemListSummaryVO {
    private String id;
    private String name;
    private String description;
    private String authorId;
    private String authorName;
    private String authorUsername;
    private Boolean isPublic;
    private Boolean isFeatured;
    private String bannerTag;
    private String bannerIcon;
    private String bannerTheme;
    private Integer bannerOrder;
    private Integer problemCount;
    private Boolean isSaved;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
