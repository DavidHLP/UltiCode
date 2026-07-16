package com.ulticode.modules.notification.intent;

import com.ulticode.modules.notification.entity.enums.NotificationCategory;
import com.ulticode.modules.websocket.notification.dto.NotificationPayload;

import java.util.Map;

/**
 * Intent for security / system-critical alerts (e.g. password changed,
 * suspicious login detected). Reserved by ADR-004 §2.1.
 *
 * <p><b>Status: reserved, no automatic producer yet.</b> The intent type and
 * all three channel projections (in-app, WebSocket, email) are implemented,
 * but no business code constructs it — the auth module has no suspicious-
 * login / device-fingerprint detection today. The only live emitter of
 * {@code SECURITY}-category notifications is {@code AdminNotificationService}
 * (admin broadcast), which force-delivers and does not go through this
 * intent. Wiring automatic security events (new-device login, credential
 * change) is a future feature; see ADR-004 §2.1.
 *
 * <p>Callers should set {@code category = SECURITY} on the record so the
 * dispatcher's preference filter applies consistently. A future
 * {@code bypassPreference} flag would belong here if force-delivery beyond
 * admin broadcast is ever needed; for now all alerts honor the SECURITY
 * preference.
 */
public record SystemAlertIntent(
        String userId,
        String alertKey,
        String title,
        String body,
        String link,
        NotificationCategory category
) implements NotificationIntent {

    @Override
    public String intentId() {
        return "system-alert:" + userId + ":" + alertKey;
    }

    @Override
    public String wireType() {
        return "SYSTEM";
    }

    @Override
    public NotificationPayload toPushPayload() {
        return NotificationPayload.of(
                intentId(),
                "SYSTEM",
                title,
                body == null ? "" : body,
                Map.of("alertKey", alertKey));
    }
}
