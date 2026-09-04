package com.ulticode.modules.user.port;

import com.ulticode.app.api.dto.NotificationUserInfo;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Read port for user info and Auth-owned recipient resolution needed by
 * notification and email modules.
 */
public interface UserReadPort {
    NotificationUserInfo findById(String userId);
    List<NotificationUserInfo> findByIds(Collection<String> userIds);

    /**
     * Return Auth-authoritative recipients for an {@code ALL} broadcast.
     */
    List<String> findAllActiveIds();
}
