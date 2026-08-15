package com.ulticode.modules.admin.port.adapter;

import com.ulticode.app.api.dto.NotificationAdminDTO;
import com.ulticode.app.api.service.NotificationAdminReadPort;
import com.ulticode.app.api.service.NotificationServiceContract;
import com.ulticode.common.response.PageResult;
import com.ulticode.common.rpc.RpcPolicy;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Dubbo consumer adapter registering {@link NotificationAdminReadPort} as a
 * local admin bean, backed by the notification-owner provider
 * ({@code com.ulticode.notification.dubbo.provider.NotificationAdminReadProvider}).
 *
 * <p>ADMIN-008: admin projections/services keep depending on the
 * entity-free port contract; this adapter is the only local bean of that
 * type. Query references use {@link RpcPolicy#QUERY_TIMEOUT_MS} /
 * {@link RpcPolicy#QUERY_RETRIES} per the migration guide &sect;6.4.
 *
 * @author ulticode
 */
@Primary
@Component
public class DubboNotificationAdminReadAdapter implements NotificationAdminReadPort {

    @DubboReference(group = NotificationServiceContract.DUBBO_GROUP,
            version = NotificationServiceContract.DUBBO_VERSION,
            timeout = RpcPolicy.QUERY_TIMEOUT_MS, retries = RpcPolicy.QUERY_RETRIES, check = false)
    private NotificationAdminReadPort notificationAdminReadPort;

    @Override
    public PageResult<NotificationAdminDTO> selectSystemNotifications(
            int page, int size, String keyword, String type,
            String announcementId, String sortBy, String sortOrder) {
        return notificationAdminReadPort.selectSystemNotifications(
                page, size, keyword, type, announcementId, sortBy, sortOrder);
    }

    @Override
    public PageResult<NotificationAdminDTO> selectSystemNotifications(
            int page, int size, String keyword, String type, String category,
            String announcementId, String sortBy, String sortOrder) {
        return notificationAdminReadPort.selectSystemNotifications(
                page, size, keyword, type, category, announcementId, sortBy, sortOrder);
    }

    @Override
    public NotificationAdminDTO selectById(String id) {
        return notificationAdminReadPort.selectById(id);
    }
}
