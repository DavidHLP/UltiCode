package com.ulticode.modules.notification.intent;

import com.ulticode.modules.notification.entity.enums.NotificationCategory;
import com.ulticode.modules.websocket.notification.dto.NotificationPayload;

/**
 * Sealed top-level interface for typed notification intents (ADR-004 §2.1).
 *
 * <p>Replaces the legacy {@code Map<String, Object>} envelope. Every concrete
 * intent is a record declared in this package; this interface's {@code permits}
 * clause is the exhaustive list. Adding a new intent type is a
 * source-incompatible change (sealed), so new types must be planned with the
 * rest of the channels.
 *
 * <p>The four methods are the minimum every channel needs to make a routing
 * decision and project a wire-format payload:
 * <ul>
 *   <li>{@link #userId()} — the recipient. Used by all channels.</li>
 *   <li>{@link #category()} — coarse preference category (COMMUNICATION /
 *       MARKETING / SECURITY / SYSTEM). Used by the dispatcher to consult
 *       {@code NotificationPreference} before fanning out to channels.</li>
 *   <li>{@link #intentId()} — stable idempotency key. Each concrete record
 *       derives this from its domain-natural fields (e.g. {@code
 *       submissionId + ":" + generation} for {@link SubmissionCompletedIntent})
 *       so retries yield the same key.</li>
 *   <li>{@link #toPushPayload()} — project the intent to the canonical generic
 *       {@link NotificationPayload} wire format used by the WebSocket channel
 *       for non-typed events. {@link AchievementEarnedIntent} <b>does not</b>
 *       use this method — it pushes the typed {@code BadgeEarnedPayload} via
 *       {@code BadgePushPort} because the frontend binds on the typed shape;
 *       the channel keeps that as the single remaining special case.</li>
 * </ul>
 *
 * <p>Each intent subtype owns its own wire-format projection via
 * {@link #toPushPayload()} — the channel's {@code instanceof} ladder
 * disappears for non-achievement events. Locality: payload rules live with the
 * data they describe. Adding a new intent — the channel doesn't change.
 *
 * <p>Reference: notification/intent/NotificationIntent + EmailTemplates
 * (same package); see also V20260613120000__Create_Notification_Delivery_Ledger.sql.
 */
public sealed interface NotificationIntent
        permits SubmissionCompletedIntent,
                AchievementEarnedIntent,
                ContestStartingIntent,
                FollowReceivedIntent,
                CommentReplyIntent,
                SystemAlertIntent {

    String userId();

    NotificationCategory category();

    String intentId();

    /**
     * Stable wire-format type discriminator written to the
     * {@code notification.type} column and matched by the Console/Management
     * front-end {@code normalizeType}. Each concrete intent returns one
     * canonical constant (e.g. {@code "SUBMISSION"}, {@code "CONTEST_REMINDER"},
     * {@code "FOLLOW"}) so the persisted value is independent of the Java class
     * name and survives renames. This is the single source for the {@code type}
     * column; channels MUST NOT substitute {@code getClass().getSimpleName()},
     * which leaks a Java identifier across the stack and breaks front-end
     * classification.
     */
    String wireType();

    /**
     * Project this intent to the canonical generic {@link NotificationPayload}
     * wire format used by the WebSocket channel. The default implementation is
     * an exhaustive {@code switch} on {@code this.getClass().getSimpleName()}
     * covering the five non-achievement intents — the channel calls this and
     * pushes the result without further branching.
     *
     * <p>{@link AchievementEarnedIntent} does NOT override this — the channel
     * detects it with a single {@code instanceof} and pushes the typed
     * {@code BadgeEarnedPayload} via {@code BadgePushPort}. The two-channel
     * split (typed achievement payload vs generic everything-else) is the one
     * seam the visitor pattern does not flatten, and that is the right
     * judgement call: the achievement payload is a genuinely different DTO
     * consumed by a different frontend handler.
     */
    NotificationPayload toPushPayload();
}
