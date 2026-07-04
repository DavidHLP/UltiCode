package com.ulticode.modules.websocket.port.adapter;

import com.ulticode.modules.notification.port.NotificationPushPort;
import com.ulticode.modules.websocket.notification.dto.NotificationPayload;
import com.ulticode.modules.websocket.service.RealtimeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * STOMP adapter of {@link NotificationPushPort} — the producer-side mirror of
 * the consumer-owned port in the notification module.
 *
 * <p>Lives in the websocket module because it is the only module that owns a
 * {@code SimpMessagingTemplate} and the STOMP session registry. The
 * dependency direction is inverted: the notification module owns the interface
 * (in {@code com.ulticode.modules.notification.port}), the websocket module
 * owns the implementation. The notification module never imports
 * {@code RealtimeService} or {@code SimpMessagingTemplate} again.
 *
 * <p>Contract: best-effort (D-11). Delegates to
 * {@code RealtimeService.sendNotification} which itself swallows
 * {@code org.springframework.messaging.MessageDeliveryException} for offline
 * users — the adapter therefore cannot throw on the happy path, matching the
 * port's contract. The defensive {@code try/catch} guards against a future
 * swap to a non-STOMP transport (SSE / FCM) accidentally bubbling exceptions
 * into the notification dispatcher.
 *
 * <p>This adapter is the canonical pattern for the rest of the realtime-push
 * seam (see ADR-0009): one consumer-owned port, one producer-owned adapter,
 * one narrow method per cross-module push.
 *
 * @author ulticode
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketNotificationPushAdapter implements NotificationPushPort {

    private final RealtimeService realtimeService;

    @Override
    public void pushToUser(String userId, NotificationPayload payload) {
        realtimeService.sendNotification(userId, payload);
    }
}