package com.ulticode.modules.notification.service;

import com.ulticode.common.response.PageResult;
import com.ulticode.modules.notification.dto.*;

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
     * Create a notification for a user.
     *
     * @param userId   the user ID
     * @param type     the notification type
     * @param category the notification category
     * @param title    the notification title
     * @param body     the notification body
     * @param link     the notification link
     * @return created notification
     */
    NotificationVO createNotification(String userId, String type, String category,
                                       String title, String body, String link);
}
