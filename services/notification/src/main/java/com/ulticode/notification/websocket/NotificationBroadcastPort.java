package com.ulticode.notification.websocket;

import com.ulticode.notification.api.dto.BadgeEarnedPayload;
import com.ulticode.notification.api.dto.NotificationPayload;

/** Redis-only realtime seam; App remains responsible for STOMP delivery. */
public interface NotificationBroadcastPort {

    void sendToUser(String userId, NotificationPayload payload);

    void sendBadgeToUser(String userId, BadgeEarnedPayload payload);
}
