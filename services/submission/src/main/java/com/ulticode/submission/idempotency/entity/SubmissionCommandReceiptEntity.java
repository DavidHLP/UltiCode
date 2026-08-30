package com.ulticode.submission.idempotency.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** Durable rejudge command claim/result owned by backend-submission. */
@Data
@TableName("submission_command_receipt")
public class SubmissionCommandReceiptEntity {

    @TableId
    private String id;
    private String commandId;
    private String service;
    private String operation;
    private String idempotencyKey;
    private String requestFingerprint;
    private String status;
    private String resultPayload;
    private String actorType;
    private String actorId;
    private String traceId;
    private LocalDateTime createdAt;
}
