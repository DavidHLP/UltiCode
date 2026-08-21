package com.ulticode.app.security.jwt;

import com.ulticode.common.auth.AccountInfo;
import com.ulticode.common.security.AccountReadPort;
import com.ulticode.app.user.port.UserFactsProjection;
import com.ulticode.app.user.port.UserSummaryView;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * App-local adapter implementing {@link AccountReadPort} for WebSocket
 * authentication. Reads user account state (active/banned) via the
 * App-owned {@link UserFactsProjection} Q-read to support connection-time
 * ban checks.
 *
 * <p>Closes the bean gap created by P7-RELOCATE-WEBSOCKET-001 alongside
 * {@link JwtValidationAdapter}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccountReadAdapter implements AccountReadPort {

    private final UserFactsProjection userReadMapper;

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
