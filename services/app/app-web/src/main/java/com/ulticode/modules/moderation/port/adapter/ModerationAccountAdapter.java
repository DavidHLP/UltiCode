package com.ulticode.modules.moderation.port.adapter;

import com.ulticode.app.api.dto.ModerationUserInfo;
import com.ulticode.app.api.service.ModerationAccountPort;
import com.ulticode.app.user.port.UserBanWriteMapper;
import com.ulticode.app.user.port.UserReadMapper;
import com.ulticode.app.user.port.UserSummaryView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Production adapter for {@link ModerationAccountPort}
 * (P7-RELOCATE).
 *
 * <p>Reads go through the App-owned {@link UserReadMapper} Q-read of the
 * Auth-owned {@code users} table. Ban-state writes go through the
 * transitional {@link UserBanWriteMapper} Q-write seam so the ban flag
 * flips in the same local transaction as the moderation {@code user_bans}
 * sink insert — preserving the legacy adapter semantics (a direct
 * {@code users} column update) instead of a two-RPC optimistic-lock
 * round trip through the Auth Dubbo provider.
 */
@Component
@RequiredArgsConstructor
public class ModerationAccountAdapter implements ModerationAccountPort {

    private final UserReadMapper userReadMapper;
    private final UserBanWriteMapper userBanWriteMapper;

    @Override
    public Optional<ModerationUserInfo> findById(String userId) {
        if (userId == null) {
            return Optional.empty();
        }
        UserSummaryView user = userReadMapper.selectById(userId);
        return user != null
                ? Optional.of(new ModerationUserInfo(user.id(), user.username()))
                : Optional.empty();
    }

    @Override
    public void updateBanStatus(String userId, boolean isBanned, String bannedReason) {
        if (userId == null) {
            return;
        }
        userBanWriteMapper.updateBanStatus(userId, isBanned, bannedReason);
    }
}
