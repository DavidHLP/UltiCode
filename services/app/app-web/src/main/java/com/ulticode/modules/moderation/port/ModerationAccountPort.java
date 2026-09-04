package com.ulticode.modules.moderation.port;

import com.ulticode.app.api.dto.ModerationUserInfo;

import java.util.Optional;

/**
 * Narrow Auth-owner port for moderation user lookup and ban-state commands.
 */
public interface ModerationAccountPort {
    Optional<ModerationUserInfo> findById(String userId);
    void updateBanStatus(String userId, boolean isBanned, String bannedReason,
                         String actorId, String actionId);
}
