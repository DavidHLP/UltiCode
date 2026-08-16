package com.ulticode.app.api.command;

import com.ulticode.common.tracing.IdMetadata;
import com.ulticode.common.tracing.TraceMetadata;


/**
 * Command to create a problem list. Issued by the Admin BFF against
 * {@code backend-app} {@code ProblemListAdministrationService.createProblemList}.
 *
 * @param commandId   stable command id (UUID String) for log correlation
 * @param idempotency idempotency metadata (IdMetadata.mint())
 * @param actor       who is asking / on whose behalf
 * @param trace       trace metadata (TraceMetadata.EMPTY acceptable)
 * @param name        list name (required)
 * @param description list description (optional)
 * @param isPublic    public flag (default false)
 * @param bannerTag   banner tag (optional)
 * @param bannerIcon  banner icon (optional)
 * @param bannerTheme banner theme (optional)
 * @param bannerOrder banner display order (optional)
 */
public record CreateProblemListCommand(
        String commandId,
        IdMetadata idempotency,
        ActorDelegation actor,
        TraceMetadata trace,
        String name,
        String description,
        Boolean isPublic,
        String bannerTag,
        String bannerIcon,
        String bannerTheme,
        Integer bannerOrder) implements WriteCommand {
    private static final long serialVersionUID = 1L;


    public CreateProblemListCommand {
        if (commandId == null || commandId.isBlank()) {
            throw new IllegalArgumentException("commandId is required and must be a UUID String");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name is required");
        }
        if (idempotency == null) {
            throw new IllegalArgumentException("idempotency is required (use IdMetadata.mint())");
        }
        if (actor == null) {
            throw new IllegalArgumentException("actor is required");
        }
        if (trace == null) {
            trace = TraceMetadata.EMPTY;
        }
    }
}
