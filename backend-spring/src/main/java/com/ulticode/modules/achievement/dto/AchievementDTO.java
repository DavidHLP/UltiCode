package com.ulticode.modules.achievement.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.Map;

/**
 * DTO for creating or updating an achievement.
 */
@Data
public class AchievementDTO {
    @NotBlank(message = "Achievement key is required")
    private String key;

    @NotBlank(message = "Achievement name is required")
    private String name;

    @NotBlank(message = "Achievement description is required")
    private String description;

    private String icon;

    @NotBlank(message = "Achievement category is required")
    private String category;

    @NotNull(message = "Achievement tier is required")
    @Min(value = 1, message = "Tier must be at least 1")
    private Integer tier;

    @NotNull(message = "Achievement criteria is required")
    private Map<String, Object> criteria;

    @NotNull(message = "Achievement points is required")
    @Min(value = 0, message = "Points must be non-negative")
    private Integer points;

    private Boolean isActive;
}
