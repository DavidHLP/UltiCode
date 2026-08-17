package com.ulticode.notification.api.command;

import com.ulticode.common.command.ActorDelegation;
import com.ulticode.common.command.WriteCommand;
import com.ulticode.common.tracing.IdMetadata;
import com.ulticode.common.tracing.TraceMetadata;

import java.util.List;

/**
 * Command to create a system notification/announcement. Issued by the
 * Admin BFF against {@code backend-notification}
 * {@code NotificationAdministrationService.createNotification}.
 *
 * <p>Field set mirrors {@code CreateSystemNotificationRequest}:
 * {@code title} and {@code content} are required (non-blank);
 * {@code type} and {@code target} are required; {@code category}
 * defaults to {@code SYSTEM}; {@code userIds} is required only when
 * {@code target = USERS}.
 *
 * <p>Per migration guide &sect;6.2 the command carries
 * {@code commandId}, {@link IdMetadata}, {@link ActorDelegation} and
 * {@link TraceMetadata} via the {@link WriteCommand} base contract.
 *
 * @param creatorAccountId the App-side notification creator (UUID String)
 * @param title            notification title (required)
 * @param content          notification body (required)
 * @param type             notification type, e.g. SYSTEM / CONTEST (required)
 * @param category         notification category; null defaults to SYSTEM
 * @param target           audience scope: "ALL" or "USERS" (required)
 * @param userIds          recipient list when target=USERS; null/empty otherwise
 */
public record CreateNotificationCommand(
        String commandId,
        IdMetadata idempotency,
        ActorDelegation actor,
        TraceMetadata trace,
        String creatorAccountId,
        String title,
        String content,
        String type,
        String category,
        String target,
        List<String> userIds) implements WriteCommand {
    private static final long serialVersionUID = 1L;


    public CreateNotificationCommand {
        if (commandId == null || commandId.isBlank()) {
            throw new IllegalArgumentException(
                    "commandId is required and must be a UUID String");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title is required");
        }
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("content is required");
        }
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("type is required");
        }
        if (target == null || target.isBlank()) {
            throw new IllegalArgumentException("target is required");
        }
        if (creatorAccountId == null || creatorAccountId.isBlank()) {
            throw new IllegalArgumentException(
                    "creatorAccountId is required and must be a UUID String");
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
