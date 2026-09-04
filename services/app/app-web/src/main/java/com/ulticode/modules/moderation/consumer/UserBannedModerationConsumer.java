package com.ulticode.modules.moderation.consumer;

import com.ulticode.modules.moderation.port.ModerationAccountPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Durable consumer for {@code UserBanned} integration events (P6-OUTBOX-001).
 *
 * <p>Receives events from the {@code stream:integration} consumer inbox and
 * propagates ban state mutations to the Auth owner via RPC.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserBannedModerationConsumer {

    public static final String EVENT_TYPE = "UserBanned";

    private final ModerationAccountPort accountPort;

    public void consume(Map<String, Object> payload) {
        if (payload == null) {
            throw new IllegalArgumentException("UserBanned payload must not be null");
        }
        String userId = requiredString(payload, "userId");
        Object isBannedObj = payload.get("isBanned");
        if (!(isBannedObj instanceof Boolean isBanned)) {
            throw new IllegalArgumentException("Missing or invalid boolean UserBanned field: isBanned");
        }
        String reason = (String) payload.getOrDefault("reason", "");
        String bannedById = (String) payload.getOrDefault("bannedById", "");
        String actionId = requiredString(payload, "actionId");

        accountPort.updateBanStatus(userId, isBanned, reason, bannedById, actionId);
        log.info("Successfully dispatched UserBanned event to Auth owner: userId={}, actionId={}",
                userId, actionId);
    }

    private static String requiredString(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value == null || String.valueOf(value).isBlank()) {
            throw new IllegalArgumentException("Missing UserBanned field: " + key);
        }
        return String.valueOf(value);
    }
}
