package com.ulticode.admin.api.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * System notification view object (P7-RELOCATE-ADMIN-001 vertical slice).
 */
public record AdminNotificationVO(
        String id,
        String title,
        String content,
        String type,
        String category,
        String target,
        List<String> userIds,
        LocalDateTime createdAt
) {
}
