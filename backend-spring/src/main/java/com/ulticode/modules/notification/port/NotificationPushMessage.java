package com.ulticode.modules.notification.port;

import java.time.Instant;
import java.util.Map;

/**
 * Notification-owned, transport-agnostic push message.
 *
 * <p>The intermediate projection between an {@code NotificationIntent}
 * and the wire-format DTOs owned by the transport adapters (WebSocket
 * today, possibly SSE / FCM in the future). This type replaces the
 * previous direct {@code NotificationIntent.toPushPayload() →
 * NotificationPayload} dependency that forced the notification module
 * to import a WebSocket-owned DTO.
 *
 * <p>Architecture review candidate #3 (close the notification push
 * seam):
 *
 * <ul>
 *   <li><b>Consumer-owned seam</b> &mdash; the field set lives in the
 *       notification module; transport adapters translate to their own
 *       wire format.</li>
 *   <li><b>Adapter-owned wire format</b> &mdash; the WebSocket channel
 *       translates this message into {@code NotificationPayload}; a
 *       future SSE adapter would translate to a different wire shape
 *       without touching the intent code.</li>
 *   <li><b>Polymorphism-free dispatch</b> &mdash; each intent subtype
 *       produces its own {@code NotificationPushMessage} via
 *       {@code toPushMessage()}; the channel becomes a single
 *       {@code intent.toPushMessage() → port.pushToUser(...)} call.</li>
 * </ul>
 *
 * <p>The {@code data} map carries intent-type-specific fields
 * (submission id, contest id, followee id, …). The keys are stable
 * string identifiers declared alongside each concrete intent so the
 * front-end can read them without coupling to the Java class name.
 */
public record NotificationPushMessage(
        String type,
        String title,
        String body,
        String link,
        Instant timestamp,
        Map<String, String> data
) {
    public NotificationPushMessage {
        if (data == null) {
            data = Map.of();
        }
    }
}
