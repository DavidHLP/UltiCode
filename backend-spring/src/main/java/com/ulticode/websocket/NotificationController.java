package com.ulticode.websocket;

import com.ulticode.websocket.dto.NotificationEvent;
import com.ulticode.websocket.dto.NotificationMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Controller;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket controller for handling real-time notifications.
 * Provides endpoints for subscribing to community and contest updates.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    // Track online users: userId -> sessionIds
    private final Map<String, Set<String>> userSessions = new ConcurrentHashMap<>();

    /**
     * Handle WebSocket connection event.
     * Logs connection and sends confirmation to the user.
     */
    @EventListener
    public void handleWebSocketConnectListener(SessionConnectedEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        Principal user = headerAccessor.getUser();

        if (user != null) {
            String userId = user.getName();
            String sessionId = headerAccessor.getSessionId();

            userSessions.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(sessionId);

            log.info("Client connected: sessionId={}, userId={}", sessionId, userId);

            // Send connection confirmation
            Map<String, Object> confirmationData = new HashMap<>();
            confirmationData.put("message", "Successfully connected to notification service");
            confirmationData.put("userId", userId);

            notificationService.sendToUser(userId, NotificationEvent.CONNECTED, confirmationData);
        }
    }

    /**
     * Handle WebSocket disconnection event.
     * Cleans up user session tracking.
     */
    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        Principal user = headerAccessor.getUser();

        if (user != null) {
            String userId = user.getName();
            String sessionId = headerAccessor.getSessionId();

            Set<String> sessions = userSessions.get(userId);
            if (sessions != null) {
                sessions.remove(sessionId);
                if (sessions.isEmpty()) {
                    userSessions.remove(userId);
                }
            }

            log.info("Client disconnected: sessionId={}, userId={}", sessionId, userId);
        }
    }

    /**
     * Handle connection confirmation subscription.
     * Clients subscribe to /user/queue/connected to receive connection confirmation.
     */
    @MessageMapping("/connect")
    public void handleConnect(SimpMessageHeaderAccessor headerAccessor) {
        Principal user = headerAccessor.getUser();
        if (user != null) {
            Map<String, Object> data = new HashMap<>();
            data.put("message", "Connection acknowledged");
            data.put("userId", user.getName());

            notificationService.sendToUser(user.getName(), NotificationEvent.CONNECTED, data);
        }
    }

    /**
     * Subscribe to community updates.
     * Clients send to /app/subscribe/community/{communityId}.
     */
    @MessageMapping("/subscribe/community/{communityId}")
    public void subscribeToCommunity(
            @DestinationVariable String communityId,
            SimpMessageHeaderAccessor headerAccessor) {
        Principal user = headerAccessor.getUser();
        if (user != null) {
            String userId = user.getName();
            notificationService.subscribeToCommunity(userId, communityId);
            log.info("User {} subscribed to community {}", userId, communityId);
        }
    }

    /**
     * Unsubscribe from community updates.
     * Clients send to /app/unsubscribe/community/{communityId}.
     */
    @MessageMapping("/unsubscribe/community/{communityId}")
    public void unsubscribeFromCommunity(
            @DestinationVariable String communityId,
            SimpMessageHeaderAccessor headerAccessor) {
        Principal user = headerAccessor.getUser();
        if (user != null) {
            String userId = user.getName();
            notificationService.unsubscribeFromCommunity(userId, communityId);
            log.info("User {} unsubscribed from community {}", userId, communityId);
        }
    }

    /**
     * Subscribe to contest updates.
     * Clients send to /app/subscribe/contest/{contestId}.
     */
    @MessageMapping("/subscribe/contest/{contestId}")
    public void subscribeToContest(
            @DestinationVariable String contestId,
            SimpMessageHeaderAccessor headerAccessor) {
        Principal user = headerAccessor.getUser();
        if (user != null) {
            String userId = user.getName();
            notificationService.subscribeToContest(userId, contestId);
            log.info("User {} subscribed to contest {}", userId, contestId);
        }
    }

    /**
     * Unsubscribe from contest updates.
     * Clients send to /app/unsubscribe/contest/{contestId}.
     */
    @MessageMapping("/unsubscribe/contest/{contestId}")
    public void unsubscribeFromContest(
            @DestinationVariable String contestId,
            SimpMessageHeaderAccessor headerAccessor) {
        Principal user = headerAccessor.getUser();
        if (user != null) {
            String userId = user.getName();
            notificationService.unsubscribeFromContest(userId, contestId);
            log.info("User {} unsubscribed from contest {}", userId, contestId);
        }
    }

    /**
     * Handle ping for connection health check.
     * Clients send to /app/ping.
     */
    @MessageMapping("/ping")
    public void handlePing(SimpMessageHeaderAccessor headerAccessor) {
        Principal user = headerAccessor.getUser();
        if (user != null) {
            notificationService.sendToUser(user.getName(), NotificationEvent.PONG,
                    Map.of("timestamp", System.currentTimeMillis()));
        }
    }

    /**
     * Get the number of online users.
     *
     * @return the count of online users
     */
    public int getOnlineUsersCount() {
        return userSessions.size();
    }

    /**
     * Check if a user is online.
     *
     * @param userId the user ID to check
     * @return true if the user has active WebSocket sessions
     */
    public boolean isUserOnline(String userId) {
        return userSessions.containsKey(userId);
    }
}
