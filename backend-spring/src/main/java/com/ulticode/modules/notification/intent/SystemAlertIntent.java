package com.ulticode.modules.notification.intent;

import com.ulticode.modules.notification.entity.enums.NotificationCategory;

/**
 * Intent emitted for security / system-critical alerts that must reach the
 * user regardless of category preference (e.g. password changed, suspicious
 * login detected). Reserved by ADR-004 §2.1.
 *
 * <p>Callers should still set {@code category = SECURITY} on the record so
 * preference filtering behaves consistently. The {@code bypassPreference}
 * flag — if the team needs it — would belong here as a future extension; for
 * now all alerts honor the SECURITY preference.
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
}
