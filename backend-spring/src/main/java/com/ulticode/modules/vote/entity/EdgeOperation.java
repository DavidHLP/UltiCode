package com.ulticode.modules.vote.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.ulticode.modules.vote.entity.enums.EdgeOperationTargetType;
import com.ulticode.modules.vote.entity.enums.EdgeOperationType;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * EdgeOperation entity representing the edge_operations table.
 * Maps to the existing database schema from NestJS application.
 * Used for tracking user interactions like votes, views, and analysis.
 */
@Data
@TableName("edge_operations")
public class EdgeOperation {

    /**
     * Unique identifier (UUID)
     */
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /**
     * ID of the target (problem, solution, post, or comment)
     */
    @TableField("target_id")
    private String targetId;

    /**
     * Type of the target
     */
    @TableField("target_type")
    private EdgeOperationTargetType targetType;

    /**
     * ID of the user who performed the operation
     */
    @TableField("operator_id")
    private String operatorId;

    /**
     * Type of operation performed
     */
    @TableField("operation_type")
    private EdgeOperationType operationType;

    /**
     * Record creation timestamp
     */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
