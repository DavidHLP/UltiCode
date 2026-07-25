package com.ulticode.modules.problemlist.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * View object for category summary response.
 */
@Data
public class CategorySummaryVO {
    private String id;
    private String userId;
    private String name;
    private String description;
    private String icon;
    private String color;
    private Integer sortOrder;
    private Integer listCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
