package com.ulticode.modules.learningplan.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * App-owned durable learning plan.
 *
 * <p>Maps {@code app.learning_plans}. One row per confirmed
 * {@code (user_id, idempotency_key)} pair; the row is immutable after insert —
 * a replay of the same key and payload returns the stored row unchanged.
 */
@Data
@TableName("learning_plans")
public class LearningPlan {

    /** Row identifier (canonical UUID). */
    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    /** Owner account id (Auth contract reference, never a cross-schema FK). */
    @TableField("user_id")
    private String userId;

    /** Canonical lowercase UUID supplied by the caller's {@code Idempotency-Key} header. */
    @TableField("idempotency_key")
    private String idempotencyKey;

    /** SHA-256 of the canonical payload array, used to detect key reuse with a different body. */
    @TableField("request_fingerprint")
    private String requestFingerprint;

    /** Source submission id; owned by the Submission service, verified before the write. */
    @TableField("source_submission_id")
    private String sourceSubmissionId;

    /** Caller-declared draft version; confirmed by the caller, never invented by the writer. */
    @TableField("draft_version")
    private Integer draftVersion;

    private String title;

    private String content;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
