package com.ulticode.modules.email.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.ulticode.modules.email.constants.EmailStatus;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Email log entity.
 * Tracks all email sending attempts and their status.
 */
@Data
@TableName("email_logs")
public class EmailLog {

    /**
     * Unique log identifier
     */
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /**
     * Template ID used (if any)
     */
    private String templateId;

    /**
     * Recipient email address
     */
    private String recipient;

    /**
     * Email subject
     */
    private String subject;

    /**
     * Email status (PENDING, SENT, FAILED)
     */
    private EmailStatus status;

    /**
     * Timestamp when email was sent
     */
    private LocalDateTime sentAt;

    /**
     * Error message if sending failed
     */
    private String error;

    /**
     * Log creation timestamp
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
