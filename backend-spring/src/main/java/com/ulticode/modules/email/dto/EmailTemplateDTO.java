package com.ulticode.modules.email.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO for email template responses.
 */
@Data
public class EmailTemplateDTO {

    private String id;
    private String name;
    private String subject;
    private String body;
    private List<String> variables;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
