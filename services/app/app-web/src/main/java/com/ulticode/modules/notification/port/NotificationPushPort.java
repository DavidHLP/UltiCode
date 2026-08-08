package com.ulticode.modules.notification.port;

import com.ulticode.app.api.dto.NotificationPayload;

/**
 * Push port the notification module uses to deliver a real-time
 * {@link NotificationPayload} to a user's STOMP queue.
 *
 * <p>Replaces the direct dependency the notification service previously had
 * on {@code com.ulticode.modules.websocket.service.RealtimeService}: the
 * WebSocket push is owned by {@code WebSocketNotificationChannel} through
 * this port so failure isolation works per-channel. The notification
 * module's {@code modules.websocket.*} import set collapses to one
 * remaining symbol — {@link NotificationPayload} — the wire format that
 * must stay shared.
 *
 * <p>The deletion test passes: removing this port would force
 * {@code NotificationServiceImpl} to re-import {@code RealtimeService}
 * (and indirectly the {@code SimpMessagingTemplate} configuration it depends
 * on), restart a STOMP broker for unit tests, and lose the ability to swap
 * the transport (SSE / FCM) without touching the consumer.
 *
 * <p>Contract: best-effort, fire-and-forget (D-11). Implementations must not
 * throw on missing sessions — offline users simply miss the push; the
 * notification row in the database is the durable record. This matches the
 * established contract on the producer-side
 * {@code RealtimeService.sendNotification}.
 *
 * @author ulticode
 */
public interface NotificationPushPort {

    /**
     * Push a notification envelope to the user's STOMP queue.
     *
     * <p>Implementations MUST NOT throw on a missing or disconnected session —
     * the call is best-effort and the row in the {@code notifications} table
     * is the durable record.
     *
     * @param userId  the recipient user id (must not be {@code null})
     * @param payload the wire-format envelope (must not be {@code null})
     */
    void pushToUser(String userId, NotificationPayload payload);
}