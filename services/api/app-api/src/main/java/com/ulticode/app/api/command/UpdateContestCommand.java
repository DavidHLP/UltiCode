package com.ulticode.app.api.command;

import com.ulticode.app.api.dto.ContestProblemInputDTO;
import com.ulticode.common.tracing.IdMetadata;
import com.ulticode.common.tracing.TraceMetadata;

import java.io.Serializable;
import java.util.List;

/**
 * Partial update command for the App-owned contest store. Nullable business
 * fields retain set-if-present semantics while crossing the RPC seam.
 */
public record UpdateContestCommand(
        String commandId,
        IdMetadata idempotency,
        ActorDelegation actor,
        TraceMetadata trace,
        String contestId,
        Long expectedVersion,
        String title,
        Long startEpochMs,
        Integer durationMinutes,
        String rationale,
        String description,
        Integer maxParticipants,
        Boolean isPremium,
        Boolean isPublished,
        String slug,
        String contestType,
        String scoringRuleId,
        List<Long> problemIds,
        List<ContestProblemInputDTO> problems) implements Serializable, WriteCommand {

    /** Compatibility constructor for existing producers using the minimum shape. */
    public UpdateContestCommand(
            String commandId,
            IdMetadata idempotency,
            ActorDelegation actor,
            TraceMetadata trace,
            String contestId,
            Long expectedVersion,
            String title,
            Long startEpochMs,
            Integer durationMinutes,
            String rationale) {
        this(commandId, idempotency, actor, trace, contestId, expectedVersion, title,
                startEpochMs, durationMinutes, rationale, null, null, null, null,
                null, null, null, null, null);
    }

    public UpdateContestCommand {
        require(commandId, "commandId");
        require(contestId, "contestId");
        if (expectedVersion == null) {
            throw new IllegalArgumentException("expectedVersion is required");
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
