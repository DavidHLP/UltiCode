package com.ulticode.app.api.service;

import com.ulticode.app.api.dto.NotificationUserInfo;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Read port for looking up user info needed by notification and email modules.
 * Promoted from backend-app for P7-INFRA-S4-COMMS: legacy adapters need to implement it.
 */
public interface UserReadPort {
    NotificationUserInfo findById(String userId);
    List<NotificationUserInfo> findByIds(Collection<String> userIds);
}
