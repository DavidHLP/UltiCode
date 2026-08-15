package com.ulticode.notification.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.app.api.dto.BadgeEarnedPayload;
import com.ulticode.app.api.dto.NotificationPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/** Publishes the closed WebSocket broadcast envelope consumed by App. */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisNotificationBroadcastAdapter implements NotificationBroadcastPort {

    private static final String USER_DESTINATION = "/queue/notification";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${notification.websocket.broadcast.channel:${app.websocket.broadcast.channel:ulticode:ws:broadcast}}")
    private String channel;

    @Override
    public void sendToUser(String userId, NotificationPayload payload) {
        publish(userId, payload, "notification");
    }

    @Override
    public void sendBadgeToUser(String userId, BadgeEarnedPayload payload) {
        publish(userId, payload, "badge_earned");
    }

    private void publish(String userId, Object payload, String kind) {
        if (userId == null || userId.isBlank() || payload == null) {
            throw new IllegalArgumentException("Notification broadcast identity/payload is invalid");
        }
        try {
            Map<String, Object> envelope = new LinkedHashMap<>();
            envelope.put("type", "USER");
            envelope.put("destination", USER_DESTINATION);
            envelope.put("userId", userId);
            envelope.put("payloadJson", objectMapper.writeValueAsString(payload));
            envelope.put("kind", kind);
            redisTemplate.convertAndSend(channel, objectMapper.writeValueAsString(envelope));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to publish notification WebSocket envelope", exception);
        }
    }
}
