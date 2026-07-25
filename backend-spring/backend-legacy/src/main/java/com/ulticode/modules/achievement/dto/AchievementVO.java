package com.ulticode.modules.achievement.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * View object for achievement response.
 */
@Data
public class AchievementVO {
    private String id;
    private String key;
    private String name;
    private String description;
    private String icon;
    private String category;
    private Integer tier;
    private Map<String, Object> criteria;
    private Integer points;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
