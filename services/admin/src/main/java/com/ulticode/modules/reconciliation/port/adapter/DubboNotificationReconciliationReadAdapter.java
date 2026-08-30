package com.ulticode.modules.reconciliation.port.adapter;

import com.ulticode.common.rpc.RpcPolicy;
import com.ulticode.notification.api.dto.NotificationUserReferenceCountDTO;
import com.ulticode.notification.api.service.NotificationReconciliationReadPort;
import com.ulticode.notification.api.service.NotificationServiceContract;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/** Admin adapter for Notification-owner bounded reconciliation facts. */
@Component
public class DubboNotificationReconciliationReadAdapter implements NotificationReconciliationReadPort {

    @DubboReference(group = NotificationServiceContract.DUBBO_GROUP,
            version = NotificationServiceContract.DUBBO_VERSION,
            timeout = RpcPolicy.QUERY_TIMEOUT_MS, retries = RpcPolicy.QUERY_RETRIES, check = false)
    private NotificationReconciliationReadPort notificationReconciliationReadPort;

    @Override
    public List<NotificationUserReferenceCountDTO> findUserReferenceCounts(
            String afterAccountId,
            LocalDateTime createdSince,
            int limit) {
        return notificationReconciliationReadPort.findUserReferenceCounts(
                afterAccountId, createdSince, limit);
    }
}
