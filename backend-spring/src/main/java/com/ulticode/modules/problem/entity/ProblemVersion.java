package com.ulticode.modules.problem.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * ProblemVersion entity representing the problem_versions table.
 * Stores historical snapshots of problem data for versioning and rollback.
 */
@Data
@TableName("problem_versions")
public class ProblemVersion {

    /**
     * Version record unique identifier
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * Reference to the problem this version belongs to
     */
    @TableField("problem_id")
    private Long problemId;

    /**
     * Business version number
     */
    @TableField("version_number")
    private Integer versionNumber;

    /**
     * JSON snapshot of the problem data at this version
     */
    @TableField("snapshot_json")
    private String snapshotJson;

    /**
     * Type of change: CREATE, UPDATE, ROLLBACK
     */
    @TableField("change_type")
    private String changeType;

    /**
     * Summary of changes in this version
     */
    @TableField("change_summary")
    private String changeSummary;

    /**
     * User who created this version
     */
    @TableField("created_by")
    private String createdBy;

    /**
     * When this version was created
     */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}