package com.ulticode.app.dubbo.provider;

import com.ulticode.app.api.dto.NotificationAdminDTO;
import com.ulticode.app.api.service.NotificationAdminReadPort;
import com.ulticode.common.response.PageResult;
import com.ulticode.modules.notification.port.adapter.DefaultNotificationAdminReadAdapter;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;

/**
 * Dubbo Provider implementing {@link NotificationAdminReadPort} in
 * {@code backend-app}.
 *
 * <p>ADMIN-008: exposes the App-owned {@code notifications} table to
 * backend-admin as entity-free DTOs. Mirrors
 * {@link ContestAdminReadProvider} in shape.
 *
 * @author ulticode
 */
@DubboService(group = "backend-app", version = "1.0.0")
@RequiredArgsConstructor
public class NotificationAdminReadProvider implements NotificationAdminReadPort {

    private final DefaultNotificationAdminReadAdapter delegate;

    @Override
    public PageResult<NotificationAdminDTO> selectSystemNotifications(
            int page, int size, String keyword, String type,
            String announcementId, String sortBy, String sortOrder) {
        return delegate.selectSystemNotifications(
                page, size, keyword, type, announcementId, sortBy, sortOrder);
    }

    @Override
    public PageResult<NotificationAdminDTO> selectSystemNotifications(
            int page, int size, String keyword, String type, String category,
            String announcementId, String sortBy, String sortOrder) {
        return delegate.selectSystemNotifications(page, size, keyword, type, category,
                announcementId, sortBy, sortOrder);
    }

    @Override
    public NotificationAdminDTO selectById(String id) {
        return delegate.selectById(id);
    }
}
