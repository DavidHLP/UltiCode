package com.ulticode.admin.api.dto;

/**
 * Update system notification request (P7-RELOCATE-ADMIN-001 vertical slice).
 */
public record UpdateSystemNotificationRequest(
        String title,
        String content,
        String type,
        String category
) {
}
