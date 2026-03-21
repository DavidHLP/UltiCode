package com.ulticode.websocket;

import com.ulticode.websocket.dto.NotificationEvent;
import com.ulticode.websocket.dto.NotificationMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Service for sending WebSocket notifications.
 * Provides methods for sending messages to users, communities, and contests.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final SimpMessagingTemplate messagingTemplate;

    // ==================== User Notifications ====================

    /**
     * Send a notification to a specific user.
     * Messages are sent to /user/{userId}/queue/notifications.
     *
     * @param userId the target user ID
     * @param event  the event type
     * @param data   the notification payload
     */
    public void sendToUser(String userId, NotificationEvent event, Object data) {
        NotificationMessage message = NotificationMessage.of(event, data);
        messagingTemplate.convertAndSendToUser(userId, "/queue/notifications", message);
        log.debug("Sent {} notification to user {}", event, userId);
    }

    /**
     * Send a notification to multiple users.
     *
     * @param userIds the list of target user IDs
     * @param event   the event type
     * @param data    the notification payload
     */
    public void sendToUsers(List<String> userIds, NotificationEvent event, Object data) {
        userIds.forEach(userId -> sendToUser(userId, event, data));
    }

    // ==================== Submission Results ====================

    /**
     * Send submission result to a user.
     *
     * @param userId  the user ID
     * @param payload the submission result payload
     */
    public void sendSubmissionResult(String userId, Map<String, Object> payload) {
        sendToUser(userId, NotificationEvent.SUBMISSION_RESULT, payload);
    }

    // ==================== Badge Notifications ====================

    /**
     * Send badge earned notification to a user.
     *
     * @param userId  the user ID
     * @param payload the badge payload
     */
    public void sendBadgeEarned(String userId, Map<String, Object> payload) {
        sendToUser(userId, NotificationEvent.BADGE_EARNED, payload);
    }

    // ==================== System Announcements ====================

    /**
     * Send a system announcement to a specific user.
     *
     * @param userId  the user ID
     * @param payload the announcement payload
     */
    public void sendNotification(String userId, Map<String, Object> payload) {
        sendToUser(userId, NotificationEvent.SYSTEM_ANNOUNCEMENT, payload);
    }

    /**
     * Broadcast a system announcement to all connected users.
     *
     * @param payload the announcement payload
     */
    public void broadcastSystemAnnouncement(Object payload) {
        NotificationMessage message = NotificationMessage.of(NotificationEvent.SYSTEM_ANNOUNCEMENT, payload);
        messagingTemplate.convertAndSend("/topic/announcements", message);
        log.info("Broadcasted system announcement");
    }

    // ==================== Contest Notifications ====================

    /**
     * Subscribe a user to contest updates.
     * The user will receive messages sent to /topic/contest/{contestId}.
     *
     * @param userId    the user ID
     * @param contestId the contest ID
     */
    public void subscribeToContest(String userId, String contestId) {
        // Subscription is handled automatically by STOMP
        // This method can be used for additional tracking if needed
        log.debug("User {} subscribed to contest {}", userId, contestId);
    }

    /**
     * Unsubscribe a user from contest updates.
     *
     * @param userId    the user ID
     * @param contestId the contest ID
     */
    public void unsubscribeFromContest(String userId, String contestId) {
        // Unsubscription is handled automatically by STOMP
        log.debug("User {} unsubscribed from contest {}", userId, contestId);
    }

    /**
     * Broadcast a contest update to all subscribers.
     *
     * @param contestId the contest ID
     * @param payload   the contest update payload
     */
    public void broadcastToContest(String contestId, Object payload) {
        NotificationMessage message = NotificationMessage.of(NotificationEvent.CONTEST_UPDATE, payload);
        messagingTemplate.convertAndSend("/topic/contest/" + contestId, message);
        log.info("Broadcasted contest update for contest {}", contestId);
    }

    /**
     * Send contest update to specific users.
     *
     * @param contestId the contest ID
     * @param userIds   the list of user IDs to notify
     * @param payload   the contest update payload
     */
    public void sendContestUpdate(String contestId, List<String> userIds, Map<String, Object> payload) {
        payload.put("contestId", contestId);
        NotificationMessage message = NotificationMessage.of(NotificationEvent.CONTEST_UPDATE, payload);
        userIds.forEach(userId ->
                messagingTemplate.convertAndSendToUser(userId, "/queue/notifications", message));
        log.info("Sent contest update for contest {} to {} users", contestId, userIds.size());
    }

    // ==================== Community Notifications ====================

    /**
     * Subscribe a user to community updates.
     * The user will receive messages sent to /topic/community/{communityId}.
     *
     * @param userId     the user ID
     * @param communityId the community ID
     */
    public void subscribeToCommunity(String userId, String communityId) {
        // Subscription is handled automatically by STOMP
        log.debug("User {} subscribed to community {}", userId, communityId);
    }

    /**
     * Unsubscribe a user from community updates.
     *
     * @param userId     the user ID
     * @param communityId the community ID
     */
    public void unsubscribeFromCommunity(String userId, String communityId) {
        // Unsubscription is handled automatically by STOMP
        log.debug("User {} unsubscribed from community {}", userId, communityId);
    }

    /**
     * Broadcast a new post to community subscribers.
     *
     * @param communityId the community ID
     * @param payload     the post payload
     */
    public void broadcastNewPost(String communityId, Object payload) {
        NotificationMessage message = NotificationMessage.of(NotificationEvent.COMMUNITY_NEW_POST, payload);
        messagingTemplate.convertAndSend("/topic/community/" + communityId, message);
        log.info("Broadcasted new post to community {}", communityId);
    }

    /**
     * Send comment notification to post author.
     *
     * @param authorId the post author's user ID
     * @param payload  the comment payload
     */
    public void sendCommentNotification(String authorId, Map<String, Object> payload) {
        sendToUser(authorId, NotificationEvent.COMMUNITY_NEW_COMMENT, payload);
    }
}
