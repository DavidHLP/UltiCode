package com.ulticode.websocket.dto;

/**
 * Enumeration of notification event types for WebSocket communication.
 */
public enum NotificationEvent {
    /**
     * Submission result notification (e.g., code execution completed).
     */
    SUBMISSION_RESULT,

    /**
     * Contest update notification (e.g., contest started, ended, ranking changes).
     */
    CONTEST_UPDATE,

    /**
     * Badge earned notification.
     */
    BADGE_EARNED,

    /**
     * System announcement notification.
     */
    SYSTEM_ANNOUNCEMENT,

    /**
     * New post in a subscribed community.
     */
    COMMUNITY_NEW_POST,

    /**
     * New comment on a subscribed post.
     */
    COMMUNITY_NEW_COMMENT,

    /**
     * Connection confirmation event.
     */
    CONNECTED,

    /**
     * Ping/Pong event for connection health check.
     */
    PONG
}
