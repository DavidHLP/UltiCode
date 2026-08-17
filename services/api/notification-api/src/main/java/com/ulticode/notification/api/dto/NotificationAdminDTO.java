package com.ulticode.notification.api.dto;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Entity-free notification snapshot consumed by backend-admin via
 * {@link com.ulticode.notification.api.service.NotificationAdminReadPort}.
 *
 * <p>ADMIN-008: mirrors the fields the admin projection needs from the
 * Notification-owned {@code Notification} entity (list read, single read-back after
 * write, creator enrichment). {@code createdBy} is the flattened
 * {@code metadata.createdBy} string so consumers never touch the entity's
 * metadata map.
 *
 * @author ulticode
 */
public record NotificationAdminDTO(
        String id,
        String announcementId,
        String title,
        String body,
        String type,
        String category,
        LocalDateTime createdAt,
        String createdBy
) implements Serializable {
    private static final long serialVersionUID = 1L;

}
