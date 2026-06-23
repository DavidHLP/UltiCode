package com.ulticode.modules.admin.service;

import com.ulticode.common.response.PageResult;
import com.ulticode.modules.admin.dto.AdminNotificationQueryDTO;
import com.ulticode.modules.admin.dto.AdminNotificationVO;
import com.ulticode.modules.admin.dto.CreateSystemNotificationRequest;
import com.ulticode.modules.admin.dto.UpdateSystemNotificationRequest;

public interface AdminNotificationService {

    PageResult<AdminNotificationVO> listSystemNotifications(AdminNotificationQueryDTO queryDTO);

    /**
     * Broadcast a system announcement to target users.
     *
     * <p><b>Preference semantics (ADR-004 §2.3):</b> {@code SECURITY} and
     * {@code SYSTEM} categories are force-delivered to every target. For
     * {@code MARKETING} / {@code COMMUNICATION}, recipients who opted out are
     * filtered before persistence (matching the dispatcher's defaults:
     * marketing=false, communication=true for users without a preference row).
     *
     * @param request the announcement request (title, content, type, category, target)
     * @return a representative VO; when every recipient opted out no row is
     *         persisted and the VO carries only the announcement metadata
     */
    AdminNotificationVO createSystemNotification(CreateSystemNotificationRequest request);

    void deleteNotification(String id);

    AdminNotificationVO updateSystemNotification(String id, UpdateSystemNotificationRequest request);
}