package com.ulticode.modules.achievement.port;

import com.ulticode.notification.api.dto.BadgeEarnedPayload;

/**
 * Push port the achievement module uses to deliver a real-time
 * {@link BadgeEarnedPayload} to a user's STOMP queue when an achievement
 * is earned.
 *
 * <p>Replaces two cross-module leak points the achievement module had on
 * {@code com.ulticode.modules.websocket.service.RealtimeService}:
 * <ul>
 *   <li>{@code AchievementTriggerServiceImpl.sendBadgeEarnedNotification}
 *       (private method) — direct call to
 *       {@code realtimeService.sendNotification(userId, BadgeEarnedPayload.of(...))}.</li>
 *   <li>{@code AchievementNotificationListener.onAchievementEarned} legacy
 *       branch — same call shape, gated on
 *       {@code use-notification-intent=false}.</li>
 * </ul>
 *
 * <p>Two leak points into the same wire format were the worse case: two
 * production paths with no shared seam to test against. After extraction
 * both call-sites go through this port and the achievement module's
 * {@code modules.websocket.*} import set shrinks — the
 * {@link BadgeEarnedPayload} DTO stays as the wire format but no longer
 * drags {@code RealtimeService} along with it.
 *
 * <p>The deletion test passes: removing this port would force two
 * achievement files to re-import {@code RealtimeService}, lose the
 * shared test seam, and re-introduce the cross-module coupling.
 *
 * <p>Contract: best-effort, fire-and-forget. The badge row in the
 * {@code user_achievements} table is the durable record; offline users
 * simply miss the live toast. Matches {@code RealtimeService.sendNotification}.
 *
 * @author ulticode
 */
public interface BadgePushPort {

    /**
     * Push a badge-earned envelope to the user's STOMP queue.
     *
     * <p>Implementations MUST NOT throw on a missing or disconnected session.
     *
     * @param userId  the recipient user id (must not be {@code null})
     * @param payload the wire-format badge envelope (must not be {@code null})
     */
    void pushBadgeEarned(String userId, BadgeEarnedPayload payload);
}
