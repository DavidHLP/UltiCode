package com.ulticode.admin.api.service;

import com.ulticode.admin.api.dto.AdminNotificationQuery;
import com.ulticode.admin.api.dto.AdminNotificationVO;
import com.ulticode.admin.api.dto.CreateSystemNotificationRequest;
import com.ulticode.admin.api.dto.UpdateSystemNotificationRequest;
import com.ulticode.common.response.PageResult;

/**
 * Admin notification service contract (P7-RELOCATE-ADMIN-001 vertical slice).
 *
 * <p>Mirrors the legacy {@code com.ulticode.modules.admin.service.AdminNotificationService}
 * contract for the system notification CRUD operations. This interface lives in
 * backend-admin-api and is implemented by a Dubbo Provider in backend-admin.
 *
 * <p>Per ADR-P7-ADMIN-RPC-BOUNDARY: consumers in backend-legacy continue using
 * the local {@code com.ulticode.modules.admin.service.AdminNotificationService}
 * port until they migrate to backend-app, at which point they switch to
 * {@code @DubboReference} on this interface.
 *
 * <p><b>Provider implementation deferred to P7-RELOCATE-INFRA-001.</b>
 * The legacy {@code AdminNotificationServiceImpl} and its projection
 * ({@code DefaultAdminNotificationProjection}) read the {@code notification}
 * table via {@code Notification} entity + {@code NotificationMapper}, which
 * are owned by the notification module. Until that entity relocates to
 * backend-admin or backend-app (P7-RELOCATE-INFRA-001), a Provider here cannot
 * satisfy this contract without introducing a cross-module entity import that
 * violates the admin ownership boundary. The contract layer is complete and
 * locked by {@code BackendAdminApiContractShapeTest}.
 */
public interface AdminNotificationService {

    /**
     * List system notifications matching the given query criteria.
     *
     * @param query the query criteria
     * @return paginated list of system notifications
     */
    PageResult<AdminNotificationVO> listSystemNotifications(AdminNotificationQuery query);

    /**
     * Create a new system notification.
     *
     * @param request the create request
     * @return the created notification VO
     */
    AdminNotificationVO createSystemNotification(CreateSystemNotificationRequest request);

    /**
     * Delete a system notification by id.
     *
     * @param id the notification id
     */
    void deleteNotification(String id);

    /**
     * Update an existing system notification.
     *
     * @param id the notification id
     * @param request the update request
     * @return the updated notification VO
     */
    AdminNotificationVO updateSystemNotification(String id, UpdateSystemNotificationRequest request);
}
