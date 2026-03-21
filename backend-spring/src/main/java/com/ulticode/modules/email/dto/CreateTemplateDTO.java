package com.ulticode.modules.email.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * DTO for creating an email template.
 */
@Data
public class CreateTemplateDTO {

    /**
     * Template name
     */
    @NotBlank(message = "Template name is required")
    private String name;

    /**
     * Email subject (can contain {{variable}} placeholders)
     */
    @NotBlank(message = "Subject is required")
    private String subject;

    /**
     * Email body in HTML format (can contain {{variable}} placeholders)
     */
    @NotBlank(message = "Body is required")
    private String body;

    /**
     * List of variable names used in the template
     */
    private List<String> variables;
}
