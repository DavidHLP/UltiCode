package com.ulticode.app.api.dto;

import com.ulticode.app.api.command.ApplyModerationCommand.ModerationAction;
import java.io.Serializable;

/**
 * Result returned by {@code ContentModerationService.apply}.
 *
 * <p>Carries the moderation case id (UUID String), the moderation
 * action the App provider applied, and the new lifecycle state of
 * the targeted content so the Admin moderation dashboard can confirm
 * the side effect without an extra RPC.
 *
 * <p>Both action and state are typed enums (no free-form strings):
 * <ul>
 *   <li>{@link #appliedAction} reuses
 *       {@link ApplyModerationCommand.ModerationAction} so the
 *       inbound command and the outbound result are consistent on
 *       the wire;</li>
 *   <li>{@link #newContentState} uses the stable
 *       {@link ContentLifecycleState} enum so consumers do not have
 *       to parse raw strings.</li>
 * </ul>
 */
public record ModerationApplyResultDTO(
        String moderationCaseId,
        String contentId,
        ModerationAction appliedAction,
        ContentLifecycleState newContentState) implements Serializable {

    public ModerationApplyResultDTO {
        if (moderationCaseId == null || moderationCaseId.isBlank()) {
            throw new IllegalArgumentException(
                    "moderationCaseId is required and must be a UUID String");
        }
        if (contentId == null || contentId.isBlank()) {
            throw new IllegalArgumentException(
                    "contentId is required and must be a UUID String");
        }
        if (appliedAction == null) {
            throw new IllegalArgumentException("appliedAction is required");
        }
        if (newContentState == null) {
            throw new IllegalArgumentException("newContentState is required");
        }
    }
}