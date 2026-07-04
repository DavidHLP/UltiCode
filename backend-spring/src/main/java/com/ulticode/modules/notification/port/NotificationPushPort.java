package com.ulticode.modules.notification.port;

import com.ulticode.modules.websocket.notification.dto.NotificationPayload;

/**
 * Push port the notification module uses to deliver a real-time
 * {@link NotificationPayload} to a user's STOMP queue.
 *
 * <p>Replaces the direct dependency {@code NotificationServiceImpl.createNotification}
 * used to have on {@code com.ulticode.modules.websocket.service.RealtimeService}.
 * Before extraction the legacy {@code createNotification} path was the
 * <em>only</em> place where the notification module reached across into the
 * websocket module — its Javadoc already self-named this port as the target
 * architecture ("the legacy wrapper mirrors to the WebSocket
 * USER_QUEUE_NOTIFICATIONS topic; the new path pushes via
 * WebSocketNotificationChannel so failure isolation works per-channel").
 * After extraction the notification module's {@code modules.websocket.*}
 * import set collapses to one remaining symbol — {@link NotificationPayload}
 * — which is the wire format and must stay shared.
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