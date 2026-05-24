package com.ulticode.modules.admin.service;

import com.ulticode.common.response.PageResult;
import com.ulticode.modules.admin.dto.AdminNotificationQueryDTO;
import com.ulticode.modules.admin.dto.AdminNotificationVO;
import com.ulticode.modules.admin.dto.CreateSystemNotificationRequest;
import com.ulticode.modules.admin.dto.UpdateSystemNotificationRequest;

public interface AdminNotificationService {

    PageResult<AdminNotificationVO> listSystemNotifications(AdminNotificationQueryDTO queryDTO);

    AdminNotificationVO createSystemNotification(CreateSystemNotificationRequest request);

    void deleteNotification(String id);

    AdminNotificationVO updateSystemNotification(String id, UpdateSystemNotificationRequest request);
}