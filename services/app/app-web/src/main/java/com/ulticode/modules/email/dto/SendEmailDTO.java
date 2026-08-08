package com.ulticode.modules.email.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

/**
 * DTO for sending an email.
 */
@Data
public class SendEmailDTO {

    /**
     * Recipient email address
     */
    @NotBlank(message = "Recipient email is required")
    @Email(message = "Invalid email format")
    private String to;

    /**
     * Email subject (required if templateId is not provided)
     */
    private String subject;

    /**
     * Email HTML body (required if templateId is not provided)
     */
    private String html;

    /**
     * Plain text body (optional)
     */
    private String text;

    /**
     * Template ID to use (optional)
     */
    private String templateId;

    /**
     * Variables to substitute in template (required if templateId is provided)
     */
    private Map<String, Object> variables;
}
