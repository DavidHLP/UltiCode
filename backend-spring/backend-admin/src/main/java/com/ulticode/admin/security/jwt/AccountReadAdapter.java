package com.ulticode.admin.security.jwt;

import com.ulticode.app.api.dto.AccountInfo;
import com.ulticode.app.api.service.AccountReadPort;
import com.ulticode.app.user.port.UserReadMapper;
import com.ulticode.app.user.port.UserSummaryView;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Admin-local adapter implementing {@link AccountReadPort} for WebSocket
 * authentication (P7-RELOCATE). Reads user account state (active/banned)
 * via the App-owned {@link UserReadMapper} Q-read — the mapper is part of
 * the admin shell's explicit {@code @MapperScan}, so the bean wiring does
 * not depend on the excluded App security package.
 *
 * <p>Mirrors the App-side {@code com.ulticode.app.security.jwt.AccountReadAdapter}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccountReadAdapter implements AccountReadPort {

    private final UserReadMapper userReadMapper;

    @Override
    public Optional<AccountInfo> findById(String userId) {
        UserSummaryView user = userReadMapper.selectById(userId);
        if (user == null) {
            return Optional.empty();
        }
        return Optional.of(new AccountInfo(
                user.id(),
                user.username(),
                user.role(),
                Boolean.TRUE.equals(user.isActive()),
                Boolean.TRUE.equals(user.isBanned())));
    }
}
