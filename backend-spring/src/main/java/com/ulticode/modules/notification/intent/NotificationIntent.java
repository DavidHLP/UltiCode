package com.ulticode.modules.notification.intent;

import com.ulticode.modules.notification.entity.enums.NotificationCategory;

/**
 * Sealed top-level interface for typed notification intents (ADR-004 §2.1).
 *
 * <p>Replaces the legacy {@code Map<String, Object>} envelope. Every concrete
 * intent is a record declared in this package; this interface's {@code permits}
 * clause is the exhaustive list. Adding a new intent type is a
 * source-incompatible change (sealed), so new types must be planned with the
 * rest of the channels.
 *
 * <p>The three methods are the minimum every channel needs to make a routing
 * decision:
 * <ul>
 *   <li>{@link #userId()} — the recipient. Used by all channels.</li>
 *   <li>{@link #category()} — coarse preference category (COMMUNICATION /
 *       MARKETING / SECURITY / SYSTEM). Used by the dispatcher to consult
 *       {@code NotificationPreference} before fanning out to channels.</li>
 *   <li>{@link #intentId()} — stable idempotency key. Each concrete record
 *       derives this from its domain-natural fields (e.g. {@code
 *       submissionId + ":" + generation} for {@link SubmissionCompletedIntent})
 *       so retries yield the same key.</li>
 * </ul>
 *
 * <p>Channels project from the concrete record to their own wire format via
 * exhaustive {@code switch} patterns — see {@link
 * com.ulticode.modules.notification.channel.NotificationChannel#send(NotificationIntent)}.
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
}
