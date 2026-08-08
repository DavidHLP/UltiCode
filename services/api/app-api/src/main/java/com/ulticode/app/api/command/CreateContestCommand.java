package com.ulticode.app.api.command;

import com.ulticode.common.tracing.IdMetadata;
import com.ulticode.common.tracing.TraceMetadata;
import java.io.Serializable;

/**
 * Command to create a new contest. Issued by the Admin BFF against
 * {@code backend-app} {@code ContestAdministrationService.createContest}.
 *
 * <p>Per migration guide &sect;6.2 the command carries
 * {@code commandId}, {@link IdMetadata}, {@link ActorDelegation} and
 * {@link TraceMetadata} via the {@link WriteCommand} base contract.
 *
 * <p>The create-minimum field set is driven by the {@code contests}
 * table schema constraints. {@code contest_type}, {@code start_time}
 * and {@code duration_minutes} are {@code NOT NULL} with no DB default,
 * so they are required here. {@code scoring_mode} is {@code NOT NULL
 * DEFAULT 'SCORE'} &mdash; nullable on this command; when {@code null}
 * the provider applies the DB default. {@code scoring_rule_id} and
 * {@code description} are nullable on the table and deferred to the
 * provider / App HTTP API.
 *
 * <p>Optional fields with safe DB defaults ({@code penalty_per_wrong},
 * {@code is_rated}, {@code is_virtual}, {@code is_visible},
 * {@code registration_start}, {@code registration_end},
 * {@code freeze_time}, {@code max_participants}, {@code rules},
 * {@code cover_image}) go on {@link UpdateContestCommand} or App HTTP
 * API after the row exists.
 *
 * <p>Timestamps use epoch-millis (not {@code LocalDateTime}) for Dubbo
 * Triple serialization safety. Per &sect;6.4 fields only grow
 * additively, so the create-minimum is locked now.
 *
 * @param creatorAccountId the App-side contest creator (UUID String),
 *                         not the Admin BFF caller &mdash; the Admin
 *                         actor stays on {@link #actor()}
 * @param contestType      ICPC / IOI / CUSTOM (matches the DB enum,
 *                         required &mdash; no safe default)
 * @param scoringMode      SCORE / ICPC / IOI (nullable; provider
 *                         defaults to {@code SCORE} per DB default
 *                         when {@code null})
 * @param scoringRuleId    optional scoring-rule reference (nullable)
 * @param description      optional contest description (nullable)
 * @param startEpochMs     contest start time; epoch-millis (required)
 * @param durationMinutes  contest duration in minutes (required)
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
        int durationMinutes) implements Serializable, WriteCommand {

    public CreateContestCommand {
        if (commandId == null || commandId.isBlank()) {
            throw new IllegalArgumentException(
                    "commandId is required and must be a UUID String");
        }
        if (slug == null || slug.isBlank()) {
            throw new IllegalArgumentException("slug is required");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title is required");
        }
        if (creatorAccountId == null || creatorAccountId.isBlank()) {
            throw new IllegalArgumentException(
                    "creatorAccountId is required and must be a UUID String");
        }
        if (contestType == null || contestType.isBlank()) {
            throw new IllegalArgumentException(
                    "contestType is required (ICPC / IOI / CUSTOM)");
        }
        if (durationMinutes <= 0) {
            throw new IllegalArgumentException(
                    "durationMinutes must be positive");
        }
        if (idempotency == null) {
            throw new IllegalArgumentException(
                    "idempotency is required (use IdMetadata.mint() when "
                            + "no client token is available)");
        }
        if (actor == null) {
            throw new IllegalArgumentException("actor is required");
        }
    }
}
