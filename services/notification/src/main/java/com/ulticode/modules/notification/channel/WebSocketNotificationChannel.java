package com.ulticode.modules.notification.channel;

import com.ulticode.notification.api.dto.BadgeEarnedPayload;
import com.ulticode.modules.notification.intent.AchievementEarnedIntent;
import com.ulticode.modules.notification.intent.NotificationIntent;
import com.ulticode.notification.api.dto.NotificationPayload;
import com.ulticode.notification.websocket.NotificationBroadcastPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * WebSocket channel — publishes a real-time event for the App-local STOMP
 * relay through Redis.
 *
 * <p>Per ADR-004 §2.5 the WebSocket is best-effort: if the user is offline
 * the push is lost (this is the current contract across the codebase). The
 * dispatcher's ledger row is marked {@code DELIVERED} regardless because
 * the broadcast port is best-effort and the App relay decides whether a
 * local session is currently connected.
 *
 * <p>The channel depends only on the notification-owned broadcast port and
 * the shared wire-format DTOs; it does not own a WebSocket endpoint.
 *
 * <p><b>Visitor dispatch.</b> For all intents except
 * {@link AchievementEarnedIntent} the channel delegates wire-format
 * construction to {@link NotificationIntent#toPushPayload()} — the projection
 * rules live with the data they describe (each intent subtype implements its
 * own). The channel becomes a one-line dispatch:
 *
 * <pre>{@code
 *   notificationBroadcastPort.sendToUser(intent.userId(), intent.toPushPayload());
 * }</pre>
 *
 * <p>{@link AchievementEarnedIntent} remains a special case because the
 * frontend binds on the typed {@link BadgeEarnedPayload}. The two-channel split
 * (typed achievement payload vs generic everything-else) is the one seam
 * the visitor pattern does not flatten, and the right judgement call —
 * the achievement payload is a genuinely different DTO consumed by a
 * different frontend handler.
 *
 * <p>Two payload shapes are used:
 * <ul>
 *   <li>{@link NotificationPayload} — generic envelope; produced by
 *       {@link NotificationIntent#toPushPayload()}.</li>
 *   <li>{@link BadgeEarnedPayload} — the existing typed achievement event
 *       consumed by the frontend; reused as-is for backward compatibility
 *       with {@code BadgeEarnedPayload.of(...)} consumers in
 *       {@code management/} and {@code console/}.</li>
 * </ul>
 *
 * <p>Reference: notification/channel/WebSocketNotificationChannel + the
 * per-channel ledger key shape in
 * V20260613120000__Create_Notification_Delivery_Ledger.sql.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketNotificationChannel implements NotificationChannel {

    public static final String CHANNEL_ID = "websocket";

    private final NotificationBroadcastPort notificationBroadcastPort;

    @Override
    public String channelId() {
        return CHANNEL_ID;
    }

    @Override
    public boolean supports(NotificationIntent intent) {
        // All 6 intents have a meaningful WS event. Per-channel routing
        // (e.g. should contest reminders push?) is a product decision that
        // can be added later by gating here.
        return true;
    }

    @Override
    public void send(NotificationIntent intent) {
        if (intent instanceof AchievementEarnedIntent a) {
            // Achievement intents push the typed BadgeEarnedPayload through
            // the Redis broadcast port. The
            // tierSlug helper lives here because BadgeEarnedPayload's
            // expected slug enum is a websocket wire concern, not a
            // notification domain concern.
            notificationBroadcastPort.sendBadgeToUser(a.userId(),
                    BadgeEarnedPayload.of(
                            a.achievementKey(),
                            a.achievementName(),
                            a.achievementDescription(),
                            a.achievementIconUrl(),
                            tierSlug(a.achievementTier()),
                            a.userId()));
            return;
        }
        // All other intents — visitor-style dispatch. The intent owns the
        // projection; the channel just pushes.
        NotificationPayload payload = intent.toPushPayload();
        notificationBroadcastPort.sendToUser(intent.userId(), payload);
    }

    /**
     * Map the legacy tier integer to the slug used by the frontend
     * (matches {@code BadgeEarnedPayload.BadgeTier} constants).
     */
    private static String tierSlug(Integer tier) {
        if (tier == null) {
            return "bronze";
        }
        return switch (tier) {
            case 2 -> "silver";
            case 3 -> "gold";
            case 4 -> "platinum";
            default -> "bronze";
        };
    }
}
