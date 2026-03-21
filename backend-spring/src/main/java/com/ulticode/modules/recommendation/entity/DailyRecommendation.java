package com.ulticode.modules.recommendation.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Entity representing daily problem recommendations for users.
 * Maps to the daily_recommendations table.
 */
@Data
@TableName(value = "daily_recommendations", autoResultMap = true)
public class DailyRecommendation {

    /**
     * Unique identifier (UUID stored as VARCHAR)
     */
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /**
     * User ID who received this recommendation
     */
    @TableField("user_id")
    private String userId;

    /**
     * ID of the recommended problem
     */
    @TableField("problem_id")
    private Long problemId;

    /**
     * Recommendation scenario: DAILY, WEAK_POINT, CHALLENGE
     */
    private String scenario;

    /**
     * Recommendation score (0.0 to 1.0)
     */
    private Float score;

    /**
     * Human-readable reason for the recommendation
     */
    private String reason;

    /**
     * List of tags associated with this recommendation
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> tags;

    /**
     * When the recommendation was generated
     */
    @TableField("generated_at")
    private LocalDateTime generatedAt;

    /**
     * Record creation timestamp
     */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
