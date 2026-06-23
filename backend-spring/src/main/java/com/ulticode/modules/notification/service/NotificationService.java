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
     * Create a notification for a user. Also mirrors to the WebSocket
     * {@code /user/queue/notifications} topic so connected sessions receive
     * a real-time {@code NotificationPayload}. This is the legacy
     * {@code NotificationDispatchService} entry point; the new
     * {@code InAppNotificationChannel} (ADR-004 M4b) uses
     * {@link #createNotificationRowOnly} instead so the WebSocket push is
     * owned by {@code WebSocketNotificationChannel} on its own.
     *
     * <p><b>Does NOT consult {@code NotificationPreference}.</b> The row is
     * persisted unconditionally. Only {@code NotificationDispatcher} /
     * {@code NotificationDispatchService} enforce opt-out; callers that bypass
     * dispatch (e.g. {@code AdminNotificationService} force-delivery of
     * SECURITY/SYSTEM) must do so deliberately. Business code should dispatch,
     * not call this directly.
     *
     * @param userId   the user ID
     * @param type     the notification type
     * @param category the notification category
     * @param title    the notification title
     * @param body     the notification body
     * @param link     the notification link
     * @param metadata additional metadata to attach to the notification
     * @return created notification
     */
    NotificationVO createNotification(String userId, String type, String category,
                                       String title, String body, String link,
                                       Map<String, Object> metadata);

    /**
     * Insert a notification row only — no WebSocket mirror. ADR-004 M4b entry
     * point used by {@code InAppNotificationChannel}; the WebSocket push is
     * handled separately by {@code WebSocketNotificationChannel} so the
     * dispatcher fan-out controls the failure-isolation boundary.
     *
     * <p>Behavior is otherwise identical to {@link #createNotification} — same
     * defaults (isRead=false, id=ASSIGN_UUID, metadata JSON-serialized) — so
     * the existing {@code notification} row shape is unchanged from the
     * legacy path. Validation matrix (ADR-004 §4 #4): row schema unchanged.
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
