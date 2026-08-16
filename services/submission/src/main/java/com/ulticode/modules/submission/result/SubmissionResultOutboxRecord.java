package com.ulticode.modules.submission.result;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Entity for the {@code submission_result_outbox} table (P6-RESULT-001).
 *
 * <p>Each (submission_id, generation) pair gets its own immutable row.
 * A rejudge bumps the generation and creates a new row, preserving the
 * previous verdict's event history.
 */
@Data
@TableName("submission_result_outbox")
public class SubmissionResultOutboxRecord {

    @TableId(type = IdType.INPUT)
    private String id;

    @TableField("submission_id")
    private String submissionId;

    /** Fence generation (0 for legacy path). */
    private Long generation;

    @TableField("user_id")
    private String userId;

    @TableField("problem_id")
    private String problemId;

    private String verdict;

    @TableField("runtime_ms")
    private Integer runtimeMs;

    @TableField("memory_mb")
    private Double memoryMb;

    @TableField("contest_id")
    private String contestId;

    /** PENDING, CLAIMED, DELIVERED, DEAD */
    private String state;

    private Integer attempts;

    @TableField("last_error")
    private String lastError;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("claimed_at")
    private LocalDateTime claimedAt;

    @TableField("claim_owner")
    private String claimOwner;

    @TableField("delivered_at")
    private LocalDateTime deliveredAt;

    @TableField("next_retry_at")
    private LocalDateTime nextRetryAt;
}
