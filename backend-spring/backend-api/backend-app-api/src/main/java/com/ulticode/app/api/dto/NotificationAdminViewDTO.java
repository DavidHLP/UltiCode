package com.ulticode.app.api.dto;

/**
 * Read-back DTO for notification administration operations.
 *
 * <p>Returned by {@code NotificationAdministrationService.createNotification}
 * and {@code updateNotification}. Mirrors the fields of
 * {@code AdminNotificationVO} that are stable across the wire. The
 * consumer re-fetches the full VO via local projection for the HTTP
 * response shape (same pattern as {@link ContestAdminViewDTO}).
 *
 * @param notificationId  the persisted notification id (representative row)
 * @param announcementId  the announcement group id; null when every
 *                        recipient opted out (no row persisted)
 * @param title           notification title
 * @param type            notification type
 * @param category        notification category
 * @param createdEpochMs  creation time in epoch-millis
 */
public record NotificationAdminViewDTO(
        String notificationId,
        String announcementId,
        String title,
        String type,
        String category,
        long createdEpochMs) {
}
