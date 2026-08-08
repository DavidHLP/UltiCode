package com.ulticode.app.api.service;

import com.ulticode.app.api.dto.ModerationUserInfo;

import java.util.Collection;
import java.util.Map;

/**
 * Read port for moderation to look up user info.
 * Promoted from backend-app for P7-INFRA-S3: legacy adapter needs to implement it.
 */
public interface ModerationUserReadPort {
    ModerationUserInfo findById(String userId);
    Map<String, ModerationUserInfo> findByIds(Collection<String> userIds);
}
