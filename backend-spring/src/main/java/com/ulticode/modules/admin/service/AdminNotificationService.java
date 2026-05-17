package com.ulticode.modules.admin.service;

import com.ulticode.modules.admin.dto.AdminNotificationVO;
import com.ulticode.modules.admin.dto.CreateSystemNotificationRequest;
import com.ulticode.modules.admin.dto.UpdateSystemNotificationRequest;

import java.util.List;

/**
 * Service interface for admin notification operations.
 * Handles system announcements and notification management for admin panel.
 */
public interface AdminNotificationService {

    /**
     * Get all system notifications (announcements created by admins).
     * Returns notifications with creator information.
     *
     * @return list of system notification VOs
     */
    List<AdminNotificationVO> getAllSystemNotifications();

    /**
     * Create a system notification and send to target users.
     * When target is ALL, sends to all users.
     * When target is USERS, sends to specific user IDs.
     *
     * @param request the notification creation request
     * @return the created notification VO
     */
    AdminNotificationVO createSystemNotification(CreateSystemNotificationRequest request);

    /**
     * Delete a notification by ID.
     * Deletes all related notification records for users.
     *
     * @param id the notification ID
     */
    void deleteNotification(String id);

    /**
     * Update a system notification and all its user copies.
     *
     * @param id the notification ID
     * @param request the update request
     * @return the updated notification VO
     */
    AdminNotificationVO updateSystemNotification(String id, UpdateSystemNotificationRequest request);
}
