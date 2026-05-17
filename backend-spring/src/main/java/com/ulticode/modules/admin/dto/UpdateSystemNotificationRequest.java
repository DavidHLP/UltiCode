package com.ulticode.modules.admin.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Request DTO for updating system notifications.
 * Used by admins to edit existing announcements.
 */
@Data
public class UpdateSystemNotificationRequest {

    @NotBlank(message = "Title cannot be blank")
    private String title;

    @NotBlank(message = "Content cannot be blank")
    private String content;

    private String type;

    private String category;
}
