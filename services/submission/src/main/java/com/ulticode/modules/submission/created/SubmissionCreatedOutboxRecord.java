package com.ulticode.modules.submission.created;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** Durable contest-intake event row owned by backend-submission. */
@Data
@TableName("submission_created_outbox")
public class SubmissionCreatedOutboxRecord {

    @TableId(type = IdType.INPUT)
    private String id;

    @TableField("submission_id")
    private String submissionId;

    private Long generation;

    @TableField("user_id")
    private String userId;

    @TableField("problem_id")
    private String problemId;

    @TableField("contest_id")
    private String contestId;

    @TableField("virtual_session_id")
    private String virtualSessionId;

    private String language;

    @TableField("occurred_at")
    private LocalDateTime occurredAt;

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
