package com.ulticode.app.api.service;

import com.ulticode.app.api.dto.ModerationUserInfo;

import java.util.Optional;

/**
 * Narrow auth port for moderation: user lookup + ban status update.
 * Legacy adapter delegates to AuthAccountPort.
 */
public interface ModerationAccountPort {
    Optional<ModerationUserInfo> findById(String userId);
    void updateBanStatus(String userId, boolean isBanned, String bannedReason);
}
