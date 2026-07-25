package com.ulticode.app.api.dto;

/**
 * Stable lifecycle state of App-owned content (forum post, solution,
 * comment, problem note, etc.) as surfaced on
 * {@link ModerationApplyResultDTO#newContentState()}.
 *
 * <p>The enum is the contract surface for cross-service moderation
 * state &mdash; consumers (Admin moderation dashboard, App
 * notification fan-out) do not parse raw strings, they switch on the
 * enum. New states are added by appending a new constant; existing
 * constants are never renamed or repurposed.
 */
public enum ContentLifecycleState {
    /** Default state: published, user-visible. */
    VISIBLE,
    /** Hidden by a moderation decision; remains in the DB but not user-visible. */
    HIDDEN,
    /** Soft-deleted; remains in DB for audit and undo. */
    DELETED
}