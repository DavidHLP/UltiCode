package com.ulticode.admin.api.dto;

import java.util.List;

/**
 * Create system notification request (P7-RELOCATE-ADMIN-001 vertical slice).
 */
public record CreateSystemNotificationRequest(
        String title,
        String content,
        String type,
        String category,
        String target,
        List<String> userIds
) {
}
