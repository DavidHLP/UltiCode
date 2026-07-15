package com.ulticode.modules.notification.service;

import com.ulticode.common.response.PageResult;
import com.ulticode.modules.notification.dto.*;

import java.util.Map;

/**
 * Service interface for notification operations.
 */
public interface NotificationService {

    /**
     * Get paginated notifications for a user.
     *
     * @param userId the user ID
     * @param query  the query parameters
     * @return paginated notifications
     */
    PageResult<NotificationVO> list(String userId, NotificationQueryDTO query);

    /**
     * Get unread notification count for a user.
     *
     * @param userId the user ID
     * @return unread count
     */
    UnreadCountVO getUnreadCount(String userId);

    /**
     * Get notification preferences for a user.
     *
     * @param userId the user ID
     * @return notification preferences
     */
    NotificationPreferenceVO getPreferences(String userId);

    /**
     * Update notification preferences for a user.
     *
     * @param userId the user ID
     * @param dto    the preference update data
     * @return updated preferences
     */
    NotificationPreferenceVO updatePreferences(String userId, UpdateNotificationPreferenceDTO dto);

    /**
     * Mark all notifications as read for a user.
     *
     * @param userId the user ID
     */
    void markAllRead(String userId);

    /**
     * Clear all notifications for a user.
     *
     * @param userId the user ID
     */
    void clearAll(String userId);

    /**
     * Update a single notification.
     *
     * @param userId           the user ID
     * @param notificationId   the notification ID
     * @param dto              the update data
     * @return updated notification
     */
    NotificationVO updateNotification(String userId, String notificationId, UpdateNotificationDTO dto);

    /**
     * Delete a single notification.
     *
     * @param userId           the user ID
     * @param notificationId   the notification ID
     */
    void deleteNotification(String userId, String notificationId);

    /**
     * Insert a notification row only — no WebSocket mirror. Entry point used
     * by {@code InAppNotificationChannel}; the WebSocket push is handled
     * separately by {@code WebSocketNotificationChannel} so the dispatcher
     * fan-out controls the failure-isolation boundary.
     *
     * <p><b>Does NOT consult {@code NotificationPreference}.</b> This is the
     * channel-internal persistence primitive invoked by
     * {@code InAppNotificationChannel} <em>after</em> the dispatcher has
     * already enforced the preference gate. Do not call it directly as a
     * preference-aware send — use {@code NotificationDispatcher} instead.
     */
    NotificationVO createNotificationRowOnly(String userId, String type, String category,
                                              String title, String body, String link,
                                              Map<String, Object> metadata);
}
