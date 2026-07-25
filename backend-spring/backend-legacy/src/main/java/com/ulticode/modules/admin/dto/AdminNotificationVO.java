package com.ulticode.modules.admin.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Admin Notification View Object for system announcements.
 * Used in admin panel to display and manage system notifications.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AdminNotificationVO {

    private String id;
    private String announcementId;

    /**
     * Notification title
     */
    private String title;

    /**
     * Notification content (mapped from body field)
     */
    private String content;

    /**
     * Notification type (SYSTEM, CONTEST, SUBMISSION, etc.)
     */
    private String type;

    /**
     * Notification category (SYSTEM, COMMUNICATION, MARKETING, SECURITY)
     */
    private String category;

    /**
     * When the notification was created
     */
    private LocalDateTime createdAt;

    /**
     * Creator information (admin who created this notification)
     */
    private CreatorInfo creator;

    /**
     * Creator information nested class.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreatorInfo {
        /**
         * Creator user ID
         */
        private String id;

        /**
         * Creator username
         */
        private String username;

        /**
         * Creator avatar URL
         */
        private String avatar;
    }
}
