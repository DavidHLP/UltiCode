package com.ulticode.app.api.command;

import com.ulticode.common.tracing.IdMetadata;
import com.ulticode.common.tracing.TraceMetadata;
import java.io.Serializable;

/**
 * Command to delete a system notification and all its user copies.
 * Issued by the Admin BFF against {@code backend-app}
 * {@code NotificationAdministrationService.deleteNotification}.
 *
 * @param notificationId the notification/announcement id to delete
 */
public record DeleteNotificationCommand(
        String commandId,
        IdMetadata idempotency,
        ActorDelegation actor,
        TraceMetadata trace,
        String notificationId) implements Serializable, WriteCommand {

    public DeleteNotificationCommand {
        if (commandId == null || commandId.isBlank()) {
            throw new IllegalArgumentException(
                    "commandId is required and must be a UUID String");
        }
        if (notificationId == null || notificationId.isBlank()) {
            throw new IllegalArgumentException("notificationId is required");
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
