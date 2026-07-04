package com.ulticode.modules.websocket.port.adapter;

import com.ulticode.modules.achievement.port.BadgePushPort;
import com.ulticode.modules.websocket.notification.dto.BadgeEarnedPayload;
import com.ulticode.modules.websocket.service.RealtimeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * STOMP adapter of {@link BadgePushPort} — the producer-side mirror of the
 * consumer-owned port in the achievement module.
 *
 * <p>Sister adapter to {@link WebSocketNotificationPushAdapter}. Reuses the
 * same {@code RealtimeService.sendNotification} transport because the wire
 * destination ({@code /user/queue/notifications}) and the channel semantics
 * (best-effort, fire-and-forget) are identical. Both ports exist because
 * they live in different consumer modules with different domain meanings
 * (a {@code NotificationPayload} is a generic envelope; a
 * {@code BadgeEarnedPayload} is the typed achievement event consumed by
 * {@code console/} and {@code management/} frontends for the badge-unlocked
 * toast). Splitting them keeps the consumer modules ISP-clean and lets the
 * wire format evolve independently if needed.
 *
 * @author ulticode
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketBadgePushAdapter implements BadgePushPort {

    private final RealtimeService realtimeService;

    @Override
    public void pushBadgeEarned(String userId, BadgeEarnedPayload payload) {
        realtimeService.sendNotification(userId, payload);
    }
}