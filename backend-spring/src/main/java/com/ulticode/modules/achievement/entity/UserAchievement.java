package com.ulticode.modules.achievement.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * UserAchievement entity - represents an achievement earned by a user.
 */
@Data
@TableName("user_achievements")
public class UserAchievement {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /** User who earned the achievement */
    private String userId;

    /** The achievement that was earned */
    private String achievementId;

    /** When the achievement was earned */
    private LocalDateTime earnedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
