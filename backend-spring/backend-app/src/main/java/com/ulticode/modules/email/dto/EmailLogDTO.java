package com.ulticode.modules.email.dto;

import com.ulticode.modules.email.constants.EmailStatus;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO for email log responses.
 */
@Data
public class EmailLogDTO {

    private String id;
    private String templateId;
    private String recipient;
    private String subject;
    private EmailStatus status;
    private LocalDateTime sentAt;
    private String error;
    private LocalDateTime createdAt;
}
