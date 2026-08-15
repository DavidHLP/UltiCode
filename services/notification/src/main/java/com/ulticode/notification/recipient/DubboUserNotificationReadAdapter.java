package com.ulticode.notification.recipient;

import com.ulticode.app.api.dto.NotificationRecipientDTO;
import com.ulticode.app.api.service.UserNotificationReadPort;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;

/** Notification-side client for the App-owned recipient read seam. */
@Component
public class DubboUserNotificationReadAdapter implements UserNotificationReadPort {

    @DubboReference(group = "backend-app", version = "1.0.0", check = false)
    private UserNotificationReadPort appRecipientReadPort;

    @Override
    public NotificationRecipientDTO findById(String userId) {
        return delegate().findById(userId);
    }

    @Override
    public List<NotificationRecipientDTO> findByIds(Collection<String> userIds) {
        return delegate().findByIds(userIds);
    }

    @Override
    public List<String> findAllActiveIds() {
        return delegate().findAllActiveIds();
    }

    private UserNotificationReadPort delegate() {
        if (appRecipientReadPort == null) {
            throw new IllegalStateException("App notification recipient provider is unavailable");
        }
        return appRecipientReadPort;
    }
}
