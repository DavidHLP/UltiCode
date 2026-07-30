package com.ulticode.auth.idempotency.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("auth_command_receipt")
public class AuthCommandReceiptEntity {

    @TableId
    private String id;
    private String commandId;
    private String service;
    private String operation;
    private String idempotencyKey;
    private String requestFingerprint;
    private String status;
    private String errorCode;
    private String resultPayload;
    private String actorType;
    private String actorId;
    private String traceId;
    private LocalDateTime createdAt;
}
