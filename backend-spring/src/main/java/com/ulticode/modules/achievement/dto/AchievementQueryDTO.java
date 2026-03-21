package com.ulticode.modules.achievement.dto;

import lombok.Data;

/**
 * Query parameters for listing achievements.
 */
@Data
public class AchievementQueryDTO {
    private String category;
    private Integer tier;
    private Boolean isActive;
    private Integer page = 1;
    private Integer limit = 20;
}
