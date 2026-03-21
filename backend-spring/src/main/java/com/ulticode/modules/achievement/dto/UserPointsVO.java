package com.ulticode.modules.achievement.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * View object for user achievement points.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserPointsVO {
    private Integer totalPoints;
    private Integer achievementsEarned;
}
