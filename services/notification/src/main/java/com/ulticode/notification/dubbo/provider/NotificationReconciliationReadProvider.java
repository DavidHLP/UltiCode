package com.ulticode.notification.dubbo.provider;

import com.ulticode.notification.api.dto.NotificationUserReferenceCountDTO;
import com.ulticode.notification.api.service.NotificationReconciliationReadPort;
import com.ulticode.notification.api.service.NotificationServiceContract;
import com.ulticode.modules.notification.mapper.NotificationReconciliationReadMapper;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;

import java.time.LocalDateTime;
import java.util.List;

/** Exposes bounded Notification-owned user-reference facts to Admin reconciliation. */
@DubboService(group = NotificationServiceContract.DUBBO_GROUP,
        version = NotificationServiceContract.DUBBO_VERSION)
@RequiredArgsConstructor
public class NotificationReconciliationReadProvider implements NotificationReconciliationReadPort {

    private final NotificationReconciliationReadMapper mapper;

    @Override
    public List<NotificationUserReferenceCountDTO> findUserReferenceCounts(
            String afterAccountId,
            LocalDateTime createdSince,
            int limit) {
        if (afterAccountId == null || afterAccountId.length() > 40
                || (!afterAccountId.isEmpty() && afterAccountId.isBlank())
                || limit <= 0 || limit > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("Invalid Notification reconciliation page");
        }
        List<NotificationUserReferenceCountDTO> facts = mapper.findUserReferenceCounts(
                afterAccountId, createdSince, limit);
        if (facts == null) {
            throw new IllegalStateException("Notification reconciliation facts unavailable");
        }
        if (facts.size() > limit) {
            throw new IllegalStateException("Notification reconciliation page exceeds limit");
        }
        String previous = afterAccountId;
        for (NotificationUserReferenceCountDTO fact : facts) {
            if (fact == null || fact.accountId() == null || fact.accountId().isBlank()
                    || fact.accountId().length() > 40 || fact.rowCount() < 0
                    || fact.accountId().compareTo(previous) <= 0) {
                throw new IllegalStateException("Invalid Notification reconciliation facts");
            }
            previous = fact.accountId();
        }
        return List.copyOf(facts);
    }
}
