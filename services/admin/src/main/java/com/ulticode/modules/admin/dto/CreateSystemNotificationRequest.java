package com.ulticode.modules.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * Request DTO for creating system notifications.
 * Used by admins to send announcements to all users or specific users.
 */
@Data
public class CreateSystemNotificationRequest {

    /**
     * Notification title
     */
    @NotBlank(message = "Title cannot be blank")
    private String title;

    /**
     * Notification content/body
     */
    @NotBlank(message = "Content cannot be blank")
    private String content;

    /**
     * Notification type (SYSTEM, CONTEST, SUBMISSION, etc.)
     */
    @NotBlank(message = "Type cannot be blank")
    private String type;

    /**
     * Notification category (SYSTEM, COMMUNICATION, MARKETING, SECURITY)
     */
    private String category = "SYSTEM";

    /**
     * Target audience: "ALL" for all users, "USERS" for specific users
     */
    @NotBlank(message = "Target cannot be blank")
    private String target;

    /**
     * List of user IDs when target is "USERS"
     * Required when target = USERS, ignored when target = ALL
     */
    private List<String> userIds;
}
