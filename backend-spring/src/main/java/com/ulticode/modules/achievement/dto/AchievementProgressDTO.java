package com.ulticode.modules.achievement.dto;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * DTO representing a user's progress on an achievement.
 */
@Data
public class AchievementProgressDTO {
    private String achievementId;
    private String key;
    private String name;
    private String description;
    private String icon;
    private String category;
    private Integer tier;
    private Integer points;
    private Boolean earned;
    private LocalDateTime earnedAt;
    private Integer progress;
    private Integer target;
}
