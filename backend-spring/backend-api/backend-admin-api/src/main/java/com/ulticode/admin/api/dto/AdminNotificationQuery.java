package com.ulticode.admin.api.dto;

import java.time.LocalDateTime;

/**
 * Query criteria for system notification search
 * (P7-RELOCATE-ADMIN-001 vertical slice).
 */
public record AdminNotificationQuery(
        String type,
        String category,
        String title,
        int page,
        int size
) {
    public AdminNotificationQuery {
        if (page <= 0) {
            page = 1;
        }
        if (size <= 0 || size > 200) {
            size = 20;
        }
    }
}
