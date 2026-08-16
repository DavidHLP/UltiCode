package com.ulticode.app.api.command;

import com.ulticode.app.api.dto.ContestProblemInputDTO;
import com.ulticode.common.tracing.IdMetadata;
import com.ulticode.common.tracing.TraceMetadata;

import java.util.List;

/**
 * Command to create a contest in the App-owned contest store.
 * All fields accepted by the Admin contest write surface are carried here so
 * the BFF cannot silently lose request data at the RPC seam.
 */
public record CreateContestCommand(
        String commandId,
        IdMetadata idempotency,
        ActorDelegation actor,
        TraceMetadata trace,
        String slug,
        String title,
        String creatorAccountId,
        String contestType,
        String scoringMode,
        String scoringRuleId,
        String description,
        long startEpochMs,
        int durationMinutes,
        Integer maxParticipants,
        Boolean isPremium,
        Boolean isPublished,
        List<Long> problemIds,
        List<ContestProblemInputDTO> problems) implements WriteCommand {
    private static final long serialVersionUID = 1L;


    /** Compatibility constructor for existing producers using the minimum shape. */
    public CreateContestCommand(
            String commandId,
            IdMetadata idempotency,
            ActorDelegation actor,
            TraceMetadata trace,
            String slug,
            String title,
            String creatorAccountId,
            String contestType,
            String scoringMode,
            String scoringRuleId,
            String description,
            long startEpochMs,
            int durationMinutes) {
        this(commandId, idempotency, actor, trace, slug, title, creatorAccountId,
                contestType, scoringMode, scoringRuleId, description, startEpochMs,
                durationMinutes, null, null, null, null, null);
    }

    public CreateContestCommand {
        require(commandId, "commandId");
        require(title, "title");
        require(creatorAccountId, "creatorAccountId");
        require(contestType, "contestType");
        if (durationMinutes <= 0) {
            throw new IllegalArgumentException("durationMinutes must be positive");
        }
        if (idempotency == null) {
            throw new IllegalArgumentException("idempotency is required");
        }
        if (actor == null) {
            throw new IllegalArgumentException("actor is required");
        }
    }

    private static void require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
    }
}
