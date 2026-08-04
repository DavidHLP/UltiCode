package com.ulticode.app.api.command;

import com.ulticode.common.tracing.IdMetadata;
import com.ulticode.common.tracing.TraceMetadata;
import java.io.Serializable;

/**
 * Command to update a system notification and all its user copies.
 * Issued by the Admin BFF against {@code backend-app}
 * {@code NotificationAdministrationService.updateNotification}.
 *
 * <p>{@code title} and {@code content} are required (non-blank).
 * {@code type} and {@code category} are optional (nullable) to match
 * {@code UpdateSystemNotificationRequest}.
 *
 * @param notificationId the notification id to update
 * @param title          new title (required)
 * @param content        new body (required)
 * @param type           new type; null = unchanged
 * @param category       new category; null = unchanged
 */
public record UpdateNotificationCommand(
        String commandId,
        IdMetadata idempotency,
        ActorDelegation actor,
        TraceMetadata trace,
        String notificationId,
        String title,
        String content,
        String type,
        String category) implements Serializable, WriteCommand {

    public UpdateNotificationCommand {
        if (commandId == null || commandId.isBlank()) {
            throw new IllegalArgumentException(
                    "commandId is required and must be a UUID String");
        }
        if (notificationId == null || notificationId.isBlank()) {
            throw new IllegalArgumentException("notificationId is required");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title is required");
        }
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("content is required");
        }
        if (idempotency == null) {
            throw new IllegalArgumentException(
                    "idempotency is required (use IdMetadata.mint())");
        }
        if (actor == null) {
            throw new IllegalArgumentException("actor is required");
        }
    }
}
