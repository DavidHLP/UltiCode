package com.ulticode.modules.problem.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Problem note entity representing a user's private note for a problem.
 * Maps to the problem_notes table.
 *
 * <p>{@code createTime} / {@code updateTime} are deliberately excluded from
 * INSERT and UPDATE by MyBatis-Plus ({@link FieldStrategy#NEVER}) because the
 * project-wide {@code MybatisPlusConfig.AutoFillMetaObjectHandler} only
 * fires for the canonical field names {@code createdAt} / {@code updatedAt} /
 * {@code addedAt}. The service layer sets these timestamps manually on every
 * write — see {@code ProblemNoteServiceImpl.upsertNote}.
 *
 * @author Claude
 * @since 2026-06-11
 */
@Data
@TableName("problem_notes")
public class ProblemNote {

    /**
     * Note unique identifier (UUID)
     */
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /**
     * ID of the user who owns this note
     */
    @TableField("user_id")
    private String userId;

    /**
     * ID of the problem this note is attached to
     */
    @TableField("problem_id")
    private Long problemId;

    /**
     * Note content (plain text)
     */
    private String content;

    /**
     * Record creation timestamp. Always set by the service layer on INSERT.
     */
    @TableField(value = "create_time", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime createTime;

    /**
     * Record last-update timestamp. Always set by the service layer on INSERT and UPDATE.
     */
    @TableField(value = "update_time", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime updateTime;
}
